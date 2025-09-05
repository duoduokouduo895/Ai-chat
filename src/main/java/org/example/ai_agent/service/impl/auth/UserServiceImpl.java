package org.example.ai_agent.service.impl.auth;

import org.example.ai_agent.dto.auth.UserRegistrationDto;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.SearchService;
import org.example.ai_agent.service.UserService;
import org.example.ai_agent.util.JwtUtil;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
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

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, ReactiveStringRedisTemplate redisTemplate, SearchService searchService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.searchService = searchService; // 新增
    }

    @Override
    @Transactional
    public Mono<Void> registerUser(UserRegistrationDto registrationDto) {
        // 检查 DTO 中是否包含 guestId
        if (registrationDto.getGuestId() != null && !registrationDto.getGuestId().isEmpty()) {
            // 场景A: 游客升级
            return upgradeGuestToUser(registrationDto);
        } else {
            // 场景B: 标准新用户注册
            return registerNewUser(registrationDto);
        }
    }

    private Mono<Void> upgradeGuestToUser(UserRegistrationDto dto) {
        return userRepository.findByGuestId(dto.getGuestId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("提供的Guest ID无效或不存在")))
                .flatMap(user -> {
                    // 验证新的用户名和邮箱是否已被其他用户占用
                    Mono<Boolean> usernameTaken = userRepository.existsByUsernameAndUserIdNot(dto.getUsername(), user.getUserId());
                    Mono<Boolean> emailTaken = userRepository.existsByEmailAndUserIdNot(dto.getEmail(), user.getUserId());

                    return usernameTaken.flatMap(isUsernameTaken -> {
                        if (isUsernameTaken) {
                            return Mono.error(new IllegalArgumentException("用户名已被其他用户占用"));
                        }
                        return emailTaken;
                    }).flatMap(isEmailTaken -> {
                        if (isEmailTaken) {return Mono.error(new IllegalArgumentException("邮箱已被其他用户占 "));
                        }
                        // 更新用户信息
                        user.setUsername(dto.getUsername());
                        user.setEmail(dto.getEmail());
                        user.setPassword(passwordEncoder.encode(dto.getPassword()));
                        user.setGuest(false); // 关键：将游客状态设置为false
                        user.setGuestId(null); // 清空guestId
                        return userRepository.save(user);
                    });
                })
                // 关键改动：用户升级成功后，为其对话建立索引
                .flatMap(savedUser -> searchService.indexConversationsForUser(savedUser.getUserId()).thenReturn(savedUser))
                .then();
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
                    user.setGuest(false); // 确保新用户不是游客
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
                        // 2. 生成包含 JTI 的 token
                                            // 2. 生成包含 JTI 的 token
                    String token = jwtUtil.generateToken(user.getUsername(), user.getUserId());
                        String jti = jwtUtil.getIdFromToken(token);
                        Date expiration = jwtUtil.getExpirationDateFromToken(token);

                        if (jti == null || expiration == null) {
                            return Mono.error(new IllegalStateException("无法生成有效的 token。"));
                        }

                        // 3. 计算 Redis key 的 TTL (存活时间)
                        Duration ttl = Duration.ofMillis(expiration.getTime() - System.currentTimeMillis());
                        if (ttl.isNegative() || ttl.isZero()) {
                             return Mono.error(new IllegalStateException("Token 的过期时间无效。"));
                        }

                        // 4. 将 JTI 存入 Redis "白名单"
                        String redisKey = "token:jti:" + jti;
                        log.info("尝试将JTI存储到Redis，key: {}, value: {}, ttl: {}ms", redisKey, user.getUsername(), ttl.toMillis());
                        
                        // 添加更详细的调试信息
                        log.debug("Redis操作详情 - key类型: {}, value类型: {}, TTL类型: {}", 
                                redisKey.getClass().getSimpleName(), 
                                user.getUsername().getClass().getSimpleName(),
                                ttl.getClass().getSimpleName());
                        
                        return redisTemplate.opsForValue().set(redisKey, user.getUsername(), ttl)
                                .doOnSuccess(result -> log.info("JTI成功存储到Redis，key: {}, result: {}", redisKey, result))
                                .doOnError(error -> {
                                    log.error("JTI存储到Redis失败，key: {}, 错误类型: {}, 错误消息: {}", 
                                            redisKey, error.getClass().getSimpleName(), error.getMessage());
                                    log.error("完整错误堆栈:", error);
                                })
                                .onErrorResume(throwable -> {
                                    log.error("处理Redis存储错误，尝试降级处理: {}", throwable.getMessage());
                                    // 可以选择返回token或者抛出错误，这里我们选择返回token但记录错误
                                    return Mono.error(new RuntimeException("无法将认证信息存储到Redis，登录失败", throwable));
                                })
                                .thenReturn(token); // 只有在 Redis 写入成功后才返回 token
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

        // 3. 校验 JTI
        if (jti == null) {
            return Mono.error(new IllegalArgumentException("从Token中无法解析出JTI"));
        }

        // 4. 从 Redis 白名单中删除 JTI
        String redisKey = "token:jti:" + jti;
        return redisTemplate.delete(redisKey).then();
    }

    @Override
    public Mono<User> createGuestUser() {
        log.info("开始创建游客用户...");
        try {
            User guest = new User();
            String guestId = UUID.randomUUID().toString();
            guest.setGuest(true);
            guest.setGuestId(guestId);
            guest.setCreatedAt(java.time.LocalDateTime.now()); // 设置创建时间
            // 为满足数据库非空约束，提供占位值
            guest.setUsername("guest_" + guestId); // 使用完整的guestId确保唯一性
            guest.setEmail(guest.getUsername() + "@guest.example.com");
            guest.setPassword("GUEST_NO_PASSWORD"); // 提供一个明确的、非空的占位密码
            
            log.info("游客用户对象创建完成，guestId: {}, username: {}, userId(创建前): {}", 
                    guestId, guest.getUsername(), guest.getUserId());
            
            // 工作模式：先保存，然后立即通过唯一的guestId再查出来，以确保获取到数据库生成的userId
            return userRepository.save(guest)
                    .then(userRepository.findByGuestId(guest.getGuestId()))
                    .doOnSuccess(savedGuest -> {
                        if (savedGuest != null && savedGuest.getUserId() != null) {
                            log.info("游客用户获取成功，userId: {}, guestId: {}", savedGuest.getUserId(), savedGuest.getGuestId());
                        } else {
                            log.error("警告：通过guestId查询游客用户失败或得到的userId仍为null！");
                        }
                    })
                    .doOnError(error -> log.error("游客用户保存或后续查询失败: {}", error.getMessage(), error));
        } catch (Exception e) {
            log.error("创建游客用户过程中发生异常: {}", e.getMessage(), e);
            return Mono.error(new RuntimeException("创建游客用户失败: " + e.getMessage(), e));
        }
    }

    @Override
    public Mono<String> generateTokenForGuest(User guest) {
        log.info("开始为游客生成Token，guestId: {}, userId: {}", 
                guest != null ? guest.getGuestId() : "null",
                guest != null ? guest.getUserId() : "null");
        
        if (guest == null || !guest.isGuest() || guest.getGuestId() == null) {
            log.error("无效的游客用户对象: guest={}, isGuest={}, guestId={}", 
                    guest, guest != null ? guest.isGuest() : "null", 
                    guest != null ? guest.getGuestId() : "null");
            return Mono.error(new IllegalArgumentException("Invalid guest user object provided."));
        }

        try {
            // 游客用户创建后，user_id 由数据库自动生成，应该传递这个userId
            log.debug("调用JWT工具生成Token，subject: {}, userId: {}", guest.getGuestId(), guest.getUserId());
            String token = jwtUtil.generateToken(guest.getGuestId(), guest.getUserId());
            
            if (token == null || token.trim().isEmpty()) {
                log.error("JWT工具返回的Token为空或无效");
                return Mono.error(new IllegalStateException("JWT工具生成的Token无效"));
            }
            
            log.debug("Token生成成功，长度: {}", token.length());
            
            // 从Token中提取JTI和过期时间
            String jti = jwtUtil.getIdFromToken(token);
            Date expiration = jwtUtil.getExpirationDateFromToken(token);

            log.debug("从Token中提取信息 - JTI: {}, 过期时间: {}", jti, expiration);

            if (jti == null || expiration == null) {
                log.error("无法从Token中提取JTI或过期时间，JTI: {}, 过期时间: {}", jti, expiration);
                return Mono.error(new IllegalStateException("无法生成有效的 token。"));
            }

            Duration ttl = Duration.ofMillis(expiration.getTime() - System.currentTimeMillis());
            if (ttl.isNegative() || ttl.isZero()) {
                log.error("Token的TTL无效: {}", ttl);
                return Mono.error(new IllegalStateException("Token 的过期时间无效。"));
            }

            log.debug("Token TTL计算成功: {} 毫秒", ttl.toMillis());

            String redisKey = "token:jti:" + jti;
            log.debug("准备将JTI存储到Redis，key: {}, value: {}, ttl: {}", redisKey, guest.getGuestId(), ttl);
            
            // 在 Redis 中存储时，value 可以是 guestId 或其他标识信息
            return redisTemplate.opsForValue().set(redisKey, guest.getGuestId(), ttl)
                    .doOnSuccess(result -> log.info("JTI成功存储到Redis，key: {}", redisKey))
                    .doOnError(error -> log.error("JTI存储到Redis失败，key: {}, 错误: {}", redisKey, error.getMessage(), error))
                    .thenReturn(token);
                    
        } catch (Exception e) {
            log.error("为游客生成Token过程中发生异常: {}", e.getMessage(), e);
            return Mono.error(new RuntimeException("生成游客Token失败: " + e.getMessage(), e));
        }
    }
    
    @Override
    @Transactional
    public Mono<Integer> cleanupExpiredGuestUsers() {
        log.info("开始清理过期的游客用户...");
        try {
            // 计算1天前的时间点
            LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
            log.info("清理截止时间: {}", oneDayAgo);
            
            // 先查询要删除的游客用户数量（用于日志记录）
            return userRepository.findGuestUsersCreatedBefore(oneDayAgo)
                    .count()
                    .cast(Integer.class)
                    .flatMap(count -> {
                        if (count > 0) {
                            log.info("发现 {} 个需要清理的过期游客用户", count);
                            // 执行删除操作
                            return userRepository.deleteGuestUsersCreatedBefore(oneDayAgo)
                                    .doOnSuccess(deletedCount -> {
                                        log.info("成功清理了 {} 个过期游客用户", deletedCount);
                                    })
                                    .doOnError(error -> {
                                        log.error("清理过期游客用户时发生错误: {}", error.getMessage(), error);
                                    });
                        } else {
                            log.info("没有发现需要清理的过期游客用户");
                            return Mono.just(0);
                        }
                    });
        } catch (Exception e) {
            log.error("清理过期游客用户过程中发生异常: {}", e.getMessage(), e);
            return Mono.error(new RuntimeException("清理过期游客用户失败: " + e.getMessage(), e));
        }
    }
}
