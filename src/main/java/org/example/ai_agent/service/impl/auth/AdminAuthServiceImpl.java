package org.example.ai_agent.service.impl.auth;

import org.example.ai_agent.dto.auth.AdminProfileDto;
import org.example.ai_agent.entity.AdminUser;
import org.example.ai_agent.repository.auth.AdminUserRepository;
import org.example.ai_agent.service.AdminAuthService;
import org.example.ai_agent.util.JwtUtil;
import org.example.ai_agent.util.TokenRedisKeys;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;

    public AdminAuthServiceImpl(AdminUserRepository adminUserRepository,
                                PasswordEncoder passwordEncoder,
                                JwtUtil jwtUtil,
                                ReactiveStringRedisTemplate redisTemplate) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<String> login(String username, String password) {
        return adminUserRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new SecurityException("管理员账号或密码无效")))
                .flatMap(admin -> {
                    if (!"ACTIVE".equalsIgnoreCase(admin.getStatus())) {
                        return Mono.error(new SecurityException("管理员账号已禁用"));
                    }

                    if (!passwordEncoder.matches(password, admin.getPassword())) {
                        return Mono.error(new SecurityException("管理员账号或密码无效"));
                    }

                    return issueAdminToken(admin)
                            .flatMap(token -> adminUserRepository.updateLastLoginAt(admin.getAdminId(), LocalDateTime.now())
                                    .thenReturn(token));
                });
    }

    @Override
    public Mono<Void> logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Mono.error(new IllegalArgumentException("无效的认证请求头"));
        }

        String token = authorizationHeader.substring(7).trim();
        String jti = jwtUtil.getIdFromToken(token);

        if (jti == null) {
            return Mono.error(new IllegalArgumentException("从Token中无法解析出JTI"));
        }

        return redisTemplate.delete(TokenRedisKeys.jti(jti), TokenRedisKeys.refreshJti(jti)).then();
    }

    @Override
    public Mono<AdminProfileDto> getCurrentAdmin(String username) {
        return adminUserRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new SecurityException("管理员不存在")))
                .map(admin -> new AdminProfileDto(
                        admin.getAdminId(),
                        admin.getUsername(),
                        admin.getDisplayName(),
                        admin.getEmail()
                ));
    }

    private Mono<String> issueAdminToken(AdminUser admin) {
        String token = jwtUtil.generateAdminToken(admin.getUsername(), admin.getAdminId());
        String jti = jwtUtil.getIdFromToken(token);
        Date expiration = jwtUtil.getExpirationDateFromToken(token);

        if (jti == null || expiration == null) {
            return Mono.error(new IllegalStateException("无法生成有效的管理员Token"));
        }

        Duration ttl = Duration.ofMillis(expiration.getTime() - System.currentTimeMillis());
        if (ttl.isNegative() || ttl.isZero()) {
            return Mono.error(new IllegalStateException("管理员Token过期时间无效"));
        }

        Duration refreshTtl = jwtUtil.refreshSessionTtl(token);
        return redisTemplate.opsForValue()
                .set(TokenRedisKeys.jti(jti), "admin:" + admin.getUsername(), ttl)
                .then(redisTemplate.opsForValue()
                        .set(TokenRedisKeys.refreshJti(jti), "admin:" + admin.getUsername(), refreshTtl))
                .thenReturn(token);
    }
}
