package org.example.ai_agent.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Date;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    
    private final JwtConfig jwtConfig;
    private final SecretKey key;

    public JwtUtil(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        log.info("初始化JWT工具，配置: expiration={}, refreshWindow={}",
                jwtConfig.getExpiration(),
                jwtConfig.getRefreshWindow());
        
        try {
            // Decode the Base64 secret from properties to get the raw secret bytes
            if (jwtConfig.getSecret() == null || jwtConfig.getSecret().trim().isEmpty()) {
                throw new IllegalStateException("JWT密钥配置为空");
            }
            
            byte[] secretBytes = java.util.Base64.getUrlDecoder().decode(jwtConfig.getSecret());
            log.debug("JWT密钥解码成功");
            
            this.key = Keys.hmacShaKeyFor(secretBytes);
            log.info("JWT工具初始化成功");
            
        } catch (IllegalArgumentException e) {
            log.error("JWT密钥Base64解码失败: errorType={}", e.getClass().getSimpleName());
            throw new IllegalStateException("JWT密钥Base64解码失败", e);
        } catch (Exception e) {
            log.error("JWT工具初始化失败: errorType={}", e.getClass().getSimpleName());
            throw new IllegalStateException("JWT工具初始化失败", e);
        }
    }

    public String getIdFromToken(String token) {
        try {
            String id = getClaimFromToken(token, Claims::getId);
            log.debug("从Token中提取ID成功");
            return id;
        } catch (Exception e) {
            log.warn("从Token中提取ID失败: errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            String username = getClaimFromToken(token, Claims::getSubject);
            log.debug("从Token中提取用户名成功");
            return username;
        } catch (Exception e) {
            log.warn("从Token中提取用户名失败: errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

    public Date getExpirationDateFromToken(String token) {
        try {
            Date expiration = getClaimFromToken(token, Claims::getExpiration);
            log.debug("从Token中提取过期时间成功");
            return expiration;
        } catch (Exception e) {
            log.warn("从Token中提取过期时间失败: errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 判断 Token 是否可以用于认证业务请求。
     *
     * <p>Token 解析的其他方法会保留过期 Token 的 claims，以支持刷新窗口。
     * 这不代表过期 Token 仍然可以访问业务接口，因此认证链必须使用这个严格方法。</p>
     */
    public boolean isTokenValidForAuthentication(String token) {
        try {
            parseAuthenticationClaims(token);
            return true;
        } catch (InvalidAuthenticationTokenException e) {
            log.debug("Token不可用于认证: errorType={}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Strictly parses the claims used by the authentication chain.
     *
     * <p>This method deliberately does not reuse {@link #getAllClaimsFromToken(String)}:
     * that compatibility method preserves expired claims for the refresh flow.
     * Authentication must never receive those claims.</p>
     */
    public AuthenticationClaims parseAuthenticationClaims(String token) {
        String cleanToken = token == null ? null : token.trim();
        if (cleanToken == null || cleanToken.isEmpty()) {
            throw new InvalidAuthenticationTokenException("Token为空");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(cleanToken)
                    .getPayload();

            return toAuthenticationClaims(claims);
        } catch (JwtException | IllegalArgumentException e) {
            if (e instanceof InvalidAuthenticationTokenException invalid) {
                throw invalid;
            }
            throw new InvalidAuthenticationTokenException("Token签名或有效期无效", e);
        }
    }

    /**
     * Parses a token for the refresh flow. Signature and required claims remain
     * mandatory; an expired token is accepted only while it is inside the
     * configured refresh window.
     */
    public AuthenticationClaims parseRefreshClaims(String token) {
        String cleanToken = token == null ? null : token.trim();
        if (cleanToken == null || cleanToken.isEmpty()) {
            throw new InvalidAuthenticationTokenException("Token为空");
        }

        try {
            Claims claims = parseClaimsAllowingExpiredToken(cleanToken);
            AuthenticationClaims authenticationClaims = toAuthenticationClaims(claims);
            Date expiration = requiredExpiration(claims);
            long now = System.currentTimeMillis();
            long refreshWindow = jwtConfig.getRefreshWindow() == null ? 0L : jwtConfig.getRefreshWindow();
            if (now <= expiration.getTime() - refreshWindow || now >= expiration.getTime() + refreshWindow) {
                throw new InvalidAuthenticationTokenException("Token不在刷新窗口内");
            }
            return authenticationClaims;
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidAuthenticationTokenException("Token签名、Claim或刷新窗口无效", e);
        }
    }

    /** Returns the Redis lifetime required for a refresh-session JTI. */
    public Duration refreshSessionTtl(String token) {
        Date expiration = getExpirationDateFromToken(token);
        if (expiration == null) {
            throw new InvalidAuthenticationTokenException("Token缺少有效过期时间");
        }
        long refreshWindow = jwtConfig.getRefreshWindow() == null ? 0L : jwtConfig.getRefreshWindow();
        long ttlMillis = expiration.getTime() + refreshWindow - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            throw new InvalidAuthenticationTokenException("Token刷新会话已过期");
        }
        return Duration.ofMillis(ttlMillis);
    }

    private Claims parseClaimsAllowingExpiredToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException expired) {
            return expired.getClaims();
        }
    }

    private AuthenticationClaims toAuthenticationClaims(Claims claims) {
        String username = requiredText(claims.getSubject(), "subject");
        String jti = requiredText(claims.getId(), "jti");
        requiredExpiration(claims);
        String principalType = requiredText(claims.get("principalType", String.class), "principalType");

        return switch (principalType.toUpperCase(java.util.Locale.ROOT)) {
            case "USER" -> new AuthenticationClaims(
                    username,
                    jti,
                    "USER",
                    requiredPositiveLong(claims.get("userId"), "userId"),
                    null,
                    Boolean.TRUE.equals(claims.get("isGuest", Boolean.class)));
            case "ADMIN" -> new AuthenticationClaims(
                    username,
                    jti,
                    "ADMIN",
                    null,
                    requiredPositiveLong(claims.get("adminId"), "adminId"),
                    false);
            default -> throw new InvalidAuthenticationTokenException("principalType无效");
        };
    }

    private Date requiredExpiration(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            throw new InvalidAuthenticationTokenException("缺少必要Claim: expiration");
        }
        return expiration;
    }

    private String requiredText(String value, String claimName) {
        if (value == null || value.isBlank()) {
            throw new InvalidAuthenticationTokenException("缺少必要Claim: " + claimName);
        }
        return value;
    }

    private Long requiredPositiveLong(Object value, String claimName) {
        Long parsed = null;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else if (value instanceof String string && !string.isBlank()) {
            try {
                parsed = Long.valueOf(string.trim());
            } catch (NumberFormatException ignored) {
                // Fall through to the common invalid-claim error.
            }
        }
        if (parsed == null || parsed <= 0) {
            throw new InvalidAuthenticationTokenException("缺少有效Claim: " + claimName);
        }
        return parsed;
    }

    public record AuthenticationClaims(String username,
                                       String jti,
                                       String principalType,
                                       Long userId,
                                       Long adminId,
                                       boolean guest) {
    }

    public static class InvalidAuthenticationTokenException extends RuntimeException {
        public InvalidAuthenticationTokenException(String message) {
            super(message);
        }

        public InvalidAuthenticationTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        if (claims != null) {
            try {
                T result = claimsResolver.apply(claims);
                log.debug("从Token中提取声明成功");
                return result;
            } catch (UnsupportedOperationException e) {
                // 特殊处理UnsupportedOperationException，可能来自不可修改的Claims
                log.debug("从不可修改的Claims中提取数据失败: errorType={}", e.getClass().getSimpleName());
                return null;
            } catch (Exception e) {
                log.warn("从Token声明中提取数据失败: errorType={}", e.getClass().getSimpleName());
                return null;
            }
        }
        log.warn("Token声明为空，无法提取数据");
        return null;
    }

    private Claims getAllClaimsFromToken(String token) {
        try {
            // 防止JWT解析错误：去除token中的空白字符
            String cleanToken = token != null ? token.trim() : null;
            
            if (cleanToken == null || cleanToken.isEmpty()) {
                log.warn("Token为空或仅包含空白字符");
                return null;
            }
            
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(cleanToken)
                    .getPayload();
            log.debug("Token解析成功，包含 {} 个声明", claims.size());
            return claims;
        } catch (ExpiredJwtException e) {
            // 如果 token 只是过期了，我们仍然需要解析它的 claims 以便进行刷新判断
            log.debug("Token已过期，但可以提取声明");
            return e.getClaims();
        } catch (Exception e) {
            // 对于其他所有异常 (例如，签名无效)，我们认为 token 是完全无效的
            log.debug("Token解析失败: errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        if (expiration == null) {
            log.warn("无法获取Token过期时间，视为已过期");
            return true; // 无法获取过期时间，视为已过期
        }
        boolean expired = expiration.before(new Date());
        log.debug("Token过期检查完成: expired={}", expired);
        return expired;
    }

    public String generateToken(String username, Long userId) {
        return generateToken(username, userId, false);
    }

    public String generateToken(String username, Long userId, boolean isGuest) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("principalType", "USER");
        claims.put("isGuest", isGuest);
        if (userId != null) {
            claims.put("userId", userId);
        }
        return generateToken(username, claims);
    }

    public String generateAdminToken(String username, Long adminId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("principalType", "ADMIN");
        claims.put("adminId", adminId);
        claims.put("role", "ROLE_ADMIN");
        return generateToken(username, claims);
    }

    private String generateToken(String username, Map<String, Object> claims) {
        try {
            log.debug("开始生成JWT Token");
            
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("用户名不能为空");
            }
            
            if (jwtConfig.getExpiration() == null || jwtConfig.getExpiration() <= 0) {
                throw new IllegalStateException("JWT过期时间配置无效: " + jwtConfig.getExpiration());
            }
            
            String jti = UUID.randomUUID().toString();
            Date issuedAt = new Date(System.currentTimeMillis());
            Date expiration = new Date(System.currentTimeMillis() + jwtConfig.getExpiration());
            
            log.debug("Token生成参数已校验: expirationMs={}", jwtConfig.getExpiration());
            
            var tokenBuilder = Jwts.builder()
                    .id(jti) // 添加唯一的 JTI
                    .subject(username)
                    .issuedAt(issuedAt)
                    .expiration(expiration);

            if (claims != null) {
                claims.forEach(tokenBuilder::claim);
            }
            
            String token = tokenBuilder.signWith(key).compact();
            
            log.debug("JWT Token生成成功: tokenLength={}", token.length());
            return token;
            
        } catch (Exception e) {
            log.error("生成JWT Token失败: errorType={}", e.getClass().getSimpleName());
            throw new RuntimeException("生成JWT Token失败", e);
        }
    }

    public String refreshToken(String token) {
        parseRefreshClaims(token);
        final Claims claims = getAllClaimsFromToken(token);
        if (claims != null) {
            try {
                log.info("开始刷新JWT Token");
                
                String jti = UUID.randomUUID().toString();
                Date issuedAt = new Date(System.currentTimeMillis());
                Date expiration = new Date(System.currentTimeMillis() + jwtConfig.getExpiration());
                
                var tokenBuilder = Jwts.builder()
                        .id(jti) // 为新 token 生成新的 JTI
                        .subject(claims.getSubject())
                        .issuedAt(issuedAt)
                        .expiration(expiration);

                copyCustomClaims(claims, tokenBuilder);
                
                String newToken = tokenBuilder.signWith(key).compact();
                
                log.debug("JWT Token刷新成功");
                return newToken;
                
            } catch (Exception e) {
                log.error("刷新JWT Token失败: errorType={}", e.getClass().getSimpleName());
                throw new RuntimeException("刷新JWT Token失败", e);
            }
        }
        log.warn("无法刷新Token：claims为空");
        return null;
    }

    /**
     * 判断一个 token 是否可以被刷新。
     * <p>
     * 刷新条件:
     * 1. Token 必须能够被正确解析。
     * 2. 当前时间必须处于一个 "刷新窗口" 内。这个窗口从 (过期时间 - 刷新窗口期) 开始，到 (过期时间 + 刷新窗口期) 结束。
     *    - 这允许我们在 token 过期前的一段时间和过期后的一小段时间内进行刷新。
     *    - 例如，如果 token 在 10:30 过期，刷新窗口为 5 分钟，那么从 10:25 到 10:35 这段时间内，该 token 都可以被刷新。
     *
     * @param token 要检查的JWT
     * @return 如果 token 满足刷新条件，则返回 true。
     */
    public Boolean canTokenBeRefreshed(String token) {
        try {
            parseRefreshClaims(token);
            return true;
        } catch (Exception e) {
            // 任何解析异常都认为 token 不可刷新
            return false;
        }
    }

    /**
     * 判断一个token是否应该被刷新（更宽松的条件）
     * <p>
     * 刷新条件:
     * 1. Token必须能够被正确解析。
     * 2. Token还没有过期，但剩余时间少于刷新窗口期。
     * 3. 或者Token刚刚过期，但在宽限期内。
     *
     * @param token 要检查的JWT
     * @return 如果token满足刷新条件，则返回true。
     */
    public Boolean shouldRefreshToken(String token) {
        try {
            parseRefreshClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("检查Token刷新条件失败: errorType={}", e.getClass().getSimpleName());
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            Long userId = getClaimFromToken(token, claims -> {
                try {
                    return claims.get("userId", Long.class);
                } catch (UnsupportedOperationException e) {
                    // 如果claims是不可修改的，尝试使用更简单的方式
                    log.debug("从不可修改的Claims中获取userId时出现UnsupportedOperationException");
                    Object userIdObj = null;
                    try {
                        // 尝试直接访问而不使用get方法
                        for (String key : claims.keySet()) {
                            if ("userId".equals(key)) {
                                userIdObj = claims.get(key);
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        // 如果连遍历都不行，则返回null
                        log.debug("无法遍历Claims键值对: errorType={}", ex.getClass().getSimpleName());
                        return null;
                    }
                    
                    if (userIdObj instanceof Number) {
                        return ((Number) userIdObj).longValue();
                    } else if (userIdObj instanceof String) {
                        try {
                            return Long.parseLong((String) userIdObj);
                        } catch (NumberFormatException nfe) {
                            log.warn("无法将userId字符串转换为Long");
                            return null;
                        }
                    }
                    return null;
                }
            });
            log.debug("从Token中提取用户ID成功");
            return userId;
        } catch (Exception e) {
            log.warn("从Token中提取用户ID失败: errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

    public Long getAdminIdFromToken(String token) {
        try {
            return getClaimFromToken(token, claims -> {
                Object adminId = claims.get("adminId");
                if (adminId instanceof Number) {
                    return ((Number) adminId).longValue();
                }
                if (adminId instanceof String) {
                    return Long.parseLong((String) adminId);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("从Token中提取管理员ID失败: errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

    public Boolean getIsGuestFromToken(String token) {
        try {
            return getClaimFromToken(token, claims -> claims.get("isGuest", Boolean.class));
        } catch (Exception e) {
            log.warn("从Token中提取游客标记失败: errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

    public String getPrincipalTypeFromToken(String token) {
        try {
            String principalType = getClaimFromToken(token, claims -> claims.get("principalType", String.class));
            return principalType != null ? principalType : "USER";
        } catch (Exception e) {
            log.warn("从Token中提取principalType失败: errorType={}", e.getClass().getSimpleName());
            return "USER";
        }
    }

    private void copyCustomClaims(Claims claims, io.jsonwebtoken.JwtBuilder tokenBuilder) {
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            String key = entry.getKey();
            if (Claims.SUBJECT.equals(key) || Claims.EXPIRATION.equals(key) || Claims.ISSUED_AT.equals(key) || Claims.ID.equals(key)) {
                continue;
            }
            tokenBuilder.claim(key, entry.getValue());
        }
    }
}

