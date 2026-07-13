package org.example.ai_agent.service.impl.auth;

import org.example.ai_agent.dto.auth.UserRegistrationDto;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.SearchService;
import org.example.ai_agent.service.UserService;
import org.example.ai_agent.util.JwtUtil;
import org.example.ai_agent.util.TokenRedisKeys;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate; // 注入 Redis 模板
    private final SearchService searchService; // 新增：注入SearchService
    private final RegisteredUserCleanupService registeredUserCleanupService;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                           ReactiveStringRedisTemplate redisTemplate, SearchService searchService,
                           RegisteredUserCleanupService registeredUserCleanupService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.searchService = searchService; // 新增
        this.registeredUserCleanupService = registeredUserCleanupService;
    }

    @Override
    @Transactional
    public Mono<Void> registerUser(UserRegistrationDto registrationDto) {
        // 直接注册新用户
        return registerNewUser(registrationDto);
    }



    private Mono<Void> registerNewUser(UserRegistrationDto dto) {
        return userRepository.existsByUsername(dto.getUsername())
                .flatMap(usernameExists -> {
                    if (usernameExists) {
                        return Mono.error(new IllegalArgumentException("用户名已被占用"));
                    }
                    return userRepository.existsByEmail(dto.getEmail());
                })
                .flatMap(emailExists -> {
                    if (emailExists) {
                        return Mono.error(new IllegalArgumentException("邮箱已被占用"));
                    }
                    User user = new User();
                    user.setUsername(dto.getUsername());
                    user.setEmail(dto.getEmail());
                    user.setPassword(passwordEncoder.encode(dto.getPassword()));
                    user.setIsGuest(false); // 明确标记非游客
                    return userRepository.save(user);
                })
                .then();
    }

    @Override
    public Mono<String> loginUser(String username, String password) {
        return userRepository.findByUsername(username)
                .flatMap(user -> {
                    // 1. 验证密码
                    if (passwordEncoder.matches(password, user.getPassword())) {
                        return issueToken(user);
                    } else {
                        return Mono.error(new SecurityException("用户名或密码无效"));
                    }
                })
                .switchIfEmpty(Mono.error(new SecurityException("用户名或密码无效")));
    }
    
    @Override
    public Mono<Void> logoutUser(String authorizationHeader) {
        // 1. 校验 "Authorization" 请求头
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Mono.error(new IllegalArgumentException("无效的认证请求头"));
        }

        // 2. 提取 token
        String token = authorizationHeader.substring(7).trim(); // 去除token中的空白字符
        String jti = jwtUtil.getIdFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);

        // 3. 校验 JTI
        if (jti == null) {
            return Mono.error(new IllegalArgumentException("从Token中无法解析出JTI"));
        }

        // 4. 从 Redis 白名单中删除 JTI
        String redisKey = TokenRedisKeys.jti(jti);
        Mono<Long> deleteJti = redisTemplate.delete(redisKey);
        if (userId == null) {
            return deleteJti.then();
        }
        return deleteJti
                .then(redisTemplate.opsForSet().remove(TokenRedisKeys.userJtis(userId), jti))
                .then();
    }

    @Override
    public Mono<Void> deleteRegisteredUserAsAdmin(Long userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("用户不存在")))
                .flatMap(user -> {
                    if (Boolean.TRUE.equals(user.getIsGuest())) {
                        return Mono.error(new IllegalArgumentException("游客账号不允许在此处删除"));
                    }

                    return registeredUserCleanupService.cleanup(user)
                            .then(userRepository.deleteById(userId));
                });
    }

    @Override
    public Mono<String> createGuestAndLogin() {
        // 1. 生成游客用户名: Duog_ + UUID的前8位字符
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        String guestUsername = "Duog_" + shortUuid;
        String guestPassword = UUID.randomUUID().toString();
        
        // 2. 构造游客实体
        User guestUser = new User();
        guestUser.setUsername(guestUsername);
        guestUser.setPassword(passwordEncoder.encode(guestPassword));
        guestUser.setEmail(null); // 游客没有邮箱
        guestUser.setLastActiveAt(LocalDateTime.now());
        guestUser.setIsGuest(true); // 标记这是一个游客账号
        
        // 记录日志
        log.info("开始创建游客账号");
        
        // 3. 将游客信息保存到数据库，并在成功后直接签发游客 token
        return userRepository.save(guestUser)
                .doOnSuccess(savedUser -> log.info("游客账号创建成功"))
                .flatMap(this::issueToken)
                .onErrorResume(e -> {
                    log.error("创建游客账号失败: errorType={}", e.getClass().getSimpleName());
                    return Mono.error(new RuntimeException("无法创建游客账户，请稍后重试"));
                });
    }

    @Override
    public Mono<String> upgradeGuestUser(UserRegistrationDto registrationDto, Long guestUserId,
                                         String authorizationHeader) {
        if (guestUserId == null || guestUserId <= 0) {
            return Mono.error(new SecurityException("当前游客会话无效"));
        }

        return userRepository.findById(guestUserId)
                .switchIfEmpty(Mono.error(new SecurityException("当前游客账户不存在")))
                .flatMap(user -> {
                    if (!Boolean.TRUE.equals(user.getIsGuest())) {
                        return Mono.error(new IllegalArgumentException("当前账户已完成升级，请直接登录"));
                    }
                    return ensureRegistrationIdentityAvailable(registrationDto, guestUserId)
                            .then(Mono.defer(() -> {
                                user.setUsername(registrationDto.getUsername());
                                user.setEmail(registrationDto.getEmail());
                                user.setPassword(passwordEncoder.encode(registrationDto.getPassword()));
                                user.setIsGuest(false);
                                user.setLastActiveAt(LocalDateTime.now());
                                return userRepository.save(user);
                            }))
                            .flatMap(upgraded -> issueUpgradedToken(upgraded, authorizationHeader));
                });
    }

    private Mono<Void> ensureRegistrationIdentityAvailable(UserRegistrationDto dto, Long userId) {
        return userRepository.existsByUsernameAndUserIdNot(dto.getUsername(), userId)
                .flatMap(usernameExists -> {
                    if (Boolean.TRUE.equals(usernameExists)) {
                        return Mono.error(new IllegalArgumentException("用户名已被占用"));
                    }
                    return userRepository.existsByEmailAndUserIdNot(dto.getEmail(), userId);
                })
                .flatMap(emailExists -> Boolean.TRUE.equals(emailExists)
                        ? Mono.error(new IllegalArgumentException("邮箱已被占用"))
                        : Mono.empty());
    }

    private Mono<String> issueUpgradedToken(User user, String authorizationHeader) {
        return issueToken(user)
                .flatMap(newToken -> revokePriorSession(authorizationHeader, user.getUserId())
                        .thenReturn(newToken)
                        .onErrorResume(error -> logoutUser("Bearer " + newToken)
                                .onErrorResume(ignored -> Mono.empty())
                                .then(Mono.error(new GuestUpgradeSessionException(
                                        "游客资料已升级，但会话轮换失败，请使用新账号登录", error)))));
    }

    private Mono<Void> revokePriorSession(String authorizationHeader, Long expectedUserId) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Mono.error(new IllegalArgumentException("无效的认证请求头"));
        }
        String token = authorizationHeader.substring(7).trim();
        Long tokenUserId = jwtUtil.getUserIdFromToken(token);
        if (!Objects.equals(expectedUserId, tokenUserId)) {
            return Mono.error(new SecurityException("游客会话与当前账户不匹配"));
        }
        return logoutUser(authorizationHeader);
    }

    private Mono<String> issueToken(User user) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getUserId(), Boolean.TRUE.equals(user.getIsGuest()));
        String jti = jwtUtil.getIdFromToken(token);
        Date expiration = jwtUtil.getExpirationDateFromToken(token);

        if (jti == null || expiration == null) {
            return Mono.error(new IllegalStateException("无法生成有效的 token。"));
        }

        Duration ttl = Duration.ofMillis(expiration.getTime() - System.currentTimeMillis());
        if (ttl.isNegative() || ttl.isZero()) {
            return Mono.error(new IllegalStateException("Token 的过期时间无效。"));
        }

        String redisKey = TokenRedisKeys.jti(jti);
        log.info("尝试将JTI存储到Redis: ttlMs={}", ttl.toMillis());
        log.debug("Redis操作详情 - key类型: {}, value类型: {}, TTL类型: {}",
                redisKey.getClass().getSimpleName(),
                user.getUsername().getClass().getSimpleName(),
                ttl.getClass().getSimpleName());

        return redisTemplate.opsForValue().set(redisKey, user.getUsername(), ttl)
                .then(redisTemplate.opsForSet().add(TokenRedisKeys.userJtis(user.getUserId()), jti))
                .then(redisTemplate.expire(TokenRedisKeys.userJtis(user.getUserId()), ttl))
                .doOnSuccess(result -> log.info("JTI成功存储到Redis: result={}", result))
                .doOnError(error -> {
                    log.error("JTI存储到Redis失败: errorType={}", error.getClass().getSimpleName());
                })
                .onErrorResume(throwable -> {
                    log.error("处理Redis存储错误，拒绝签发会话: errorType={}",
                            throwable.getClass().getSimpleName());
                    return Mono.error(new RuntimeException("无法将认证信息存储到Redis，登录失败", throwable));
                })
                .thenReturn(token);
    }

}
