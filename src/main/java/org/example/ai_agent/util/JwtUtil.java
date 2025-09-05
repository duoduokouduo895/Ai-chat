package org.example.ai_agent.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.example.ai_agent.config.security.JwtConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    
    private final JwtConfig jwtConfig;
    private final SecretKey key;

    @Autowired
    public JwtUtil(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        log.info("初始化JWT工具，配置: secret长度={}, expiration={}, refreshWindow={}", 
                jwtConfig.getSecret() != null ? jwtConfig.getSecret().length() : 0,
                jwtConfig.getExpiration(),
                jwtConfig.getRefreshWindow());
        
        try {
            // Decode the Base64 secret from properties to get the raw secret bytes
            if (jwtConfig.getSecret() == null || jwtConfig.getSecret().trim().isEmpty()) {
                throw new IllegalStateException("JWT密钥配置为空");
            }
            
            byte[] secretBytes = java.util.Base64.getUrlDecoder().decode(jwtConfig.getSecret());
            log.debug("JWT密钥解码成功，原始密钥长度: {} 字节", secretBytes.length);
            
            this.key = Keys.hmacShaKeyFor(secretBytes);
            log.info("JWT工具初始化成功");
            
        } catch (IllegalArgumentException e) {
            log.error("JWT密钥Base64解码失败: {}", e.getMessage(), e);
            throw new IllegalStateException("JWT密钥Base64解码失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("JWT工具初始化失败: {}", e.getMessage(), e);
            throw new IllegalStateException("JWT工具初始化失败: " + e.getMessage(), e);
        }
    }

    public String getIdFromToken(String token) {
        try {
            String id = getClaimFromToken(token, Claims::getId);
            log.debug("从Token中提取ID: {}", id);
            return id;
        } catch (Exception e) {
            log.error("从Token中提取ID失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            String username = getClaimFromToken(token, Claims::getSubject);
            log.debug("从Token中提取用户名: {}", username);
            return username;
        } catch (Exception e) {
            log.error("从Token中提取用户名失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public Date getExpirationDateFromToken(String token) {
        try {
            Date expiration = getClaimFromToken(token, Claims::getExpiration);
            log.debug("从Token中提取过期时间: {}", expiration);
            return expiration;
        } catch (Exception e) {
            log.error("从Token中提取过期时间失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        if (claims != null) {
            try {
                T result = claimsResolver.apply(claims);
                log.debug("从Token中提取声明成功: {}", result);
                return result;
            } catch (Exception e) {
                log.error("从Token声明中提取数据失败: {}", e.getMessage(), e);
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
            log.debug("Token已过期，但可以提取声明: {}", e.getMessage());
            return e.getClaims();
        } catch (Exception e) {
            // 对于其他所有异常 (例如，签名无效)，我们认为 token 是完全无效的
            log.error("Token解析失败: {}", e.getMessage(), e);
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
        log.debug("Token过期检查: 过期时间={}, 当前时间={}, 是否过期={}", 
                expiration, new Date(), expired);
        return expired;
    }

    public String generateToken(String username, Long userId) {
        try {
            log.info("开始生成JWT Token，用户名: {}, 用户ID: {}", username, userId);
            
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("用户名不能为空");
            }
            
            if (jwtConfig.getExpiration() == null || jwtConfig.getExpiration() <= 0) {
                throw new IllegalStateException("JWT过期时间配置无效: " + jwtConfig.getExpiration());
            }
            
            String jti = UUID.randomUUID().toString();
            Date issuedAt = new Date(System.currentTimeMillis());
            Date expiration = new Date(System.currentTimeMillis() + jwtConfig.getExpiration());
            
            log.debug("Token生成参数 - JTI: {}, 签发时间: {}, 过期时间: {}, 过期时长: {}ms", 
                    jti, issuedAt, expiration, jwtConfig.getExpiration());
            
            var tokenBuilder = Jwts.builder()
                    .id(jti) // 添加唯一的 JTI
                    .subject(username)
                    .issuedAt(issuedAt)
                    .expiration(expiration);
            
            // 如果userId不为空，添加到声明中
            if (userId != null) {
                tokenBuilder.claim("userId", userId);
            }
            
            String token = tokenBuilder.signWith(key).compact();
            
            log.info("JWT Token生成成功，长度: {}, JTI: {}", token.length(), jti);
            return token;
            
        } catch (Exception e) {
            log.error("生成JWT Token失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成JWT Token失败: " + e.getMessage(), e);
        }
    }

    public String refreshToken(String token) {
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
                
                // 保留原有的用户ID声明
                if (claims.get("userId") != null) {
                    tokenBuilder.claim("userId", claims.get("userId"));
                }
                
                String newToken = tokenBuilder.signWith(key).compact();
                
                log.info("JWT Token刷新成功，新JTI: {}", jti);
                return newToken;
                
            } catch (Exception e) {
                log.error("刷新JWT Token失败: {}", e.getMessage(), e);
                throw new RuntimeException("刷新JWT Token失败: " + e.getMessage(), e);
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
            final Date expirationDate = getExpirationDateFromToken(token);
            if (expirationDate == null) {
                return false; // 无法获取过期时间，不可刷新
            }

            Date now = new Date();
            long refreshWindowMillis = jwtConfig.getRefreshWindow();

            // 计算刷新窗口的开始时间
            Date refreshWindowStart = new Date(expirationDate.getTime() - refreshWindowMillis);
            // 计算刷新窗口的结束时间 (宽限期)
            Date gracePeriodEnd = new Date(expirationDate.getTime() + refreshWindowMillis);

            // 当前时间必须在刷新窗口开始之后，并且在宽限期结束之前
            return now.after(refreshWindowStart) && now.before(gracePeriodEnd);
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
            final Date expirationDate = getExpirationDateFromToken(token);
            if (expirationDate == null) {
                log.debug("无法获取Token过期时间，不可刷新");
                return false;
            }

            Date now = new Date();
            long refreshWindowMillis = jwtConfig.getRefreshWindow();
            
            // 计算刷新窗口的开始时间（过期时间前的一段时间）
            Date refreshWindowStart = new Date(expirationDate.getTime() - refreshWindowMillis);
            // 计算宽限期结束时间（过期时间后的一段时间）
            Date gracePeriodEnd = new Date(expirationDate.getTime() + refreshWindowMillis);

            boolean shouldRefresh = now.after(refreshWindowStart) && now.before(gracePeriodEnd);
            
            if (shouldRefresh) {
                long timeToExpiry = expirationDate.getTime() - now.getTime();
                log.info("Token应该刷新 - 距离过期还有: {}ms, 刷新窗口: {}ms", 
                        timeToExpiry, refreshWindowMillis);
            }
            
            return shouldRefresh;
        } catch (Exception e) {
            log.error("检查Token刷新条件失败: {}", e.getMessage(), e);
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            Long userId = getClaimFromToken(token, claims -> claims.get("userId", Long.class));
            log.debug("从Token中提取用户ID: {}", userId);
            return userId;
        } catch (Exception e) {
            log.error("从Token中提取用户ID失败: {}", e.getMessage(), e);
            return null;
        }
    }
}

