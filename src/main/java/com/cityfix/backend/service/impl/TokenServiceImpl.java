package com.cityfix.backend.service.impl;

import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${jwt.access-token-expiration-ms:1800000}")
    private long accessTokenExpirationMs = 1_800_000L; // 30 minutes

    @Value("${jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs = 604_800_000L; // 7 days

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String REFRESH_PREFIX = "jwt:refresh:";
    private static final String USER_REFRESH_PREFIX = "jwt:user-refresh:";

    private static final DefaultRedisScript<Long> STORE_REFRESH_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])
            redis.call('SADD', KEYS[2], ARGV[2])
            redis.call('PEXPIRE', KEYS[2], ARGV[3])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> CONSUME_REFRESH_SCRIPT = new DefaultRedisScript<>("""
            local userId = redis.call('GET', KEYS[1])
            if not userId then
              return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('SREM', ARGV[1] .. userId, ARGV[2])
            return 1
            """, Long.class);

    // ─── Key ─────────────────────────────────────────────────────────────────

    private SecretKey getSigninKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ─── Token Generation ─────────────────────────────────────────────────────

    @Override
    public String generateAccessToken(User user) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim("role", user.getRole().getRoleName())
                .claim("type", TOKEN_TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(getSigninKey())
                .compact();
    }

    @Override
    public String generateRefreshToken(User user) {
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .subject(user.getEmail())
                .claim("type", TOKEN_TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
                .signWith(getSigninKey())
                .compact();

        if (user.getUserId() != null) {
            String userIdStr = user.getUserId().toString();
            redisTemplate.execute(
                    STORE_REFRESH_SCRIPT,
                    List.of(REFRESH_PREFIX + jti, USER_REFRESH_PREFIX + userIdStr),
                    userIdStr,
                    jti,
                    String.valueOf(refreshTokenExpirationMs)
            );
        }

        return token;
    }

    // ─── Claims Extraction ────────────────────────────────────────────────────

    @Override
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigninKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    @Override
    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    @Override
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    @Override
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    @Override
    public User extractUser(String token) {
        String email = extractClaim(token, Claims::getSubject);
        return userRepository.findUserByEmailAndIsDeletedFalse(email);
    }

    @Override
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    @Override
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ─── Blacklist & Refresh Token Store (Redis) ──────────────────────────────

    @Override
    public void blacklistToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            Claims claims = extractAllClaims(token);
            String jti = claims.getId();
            Date expiration = claims.getExpiration();
            if (jti == null || expiration == null) {
                return;
            }
            long ttlMillis = expiration.getTime() - System.currentTimeMillis();

            if (ttlMillis > 0) {
                redisTemplate.opsForValue()
                        .set(BLACKLIST_PREFIX + jti, "blacklisted", ttlMillis, TimeUnit.MILLISECONDS);
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // Token đã hết hạn hoặc không hợp lệ -> không cần đưa vào blacklist
        }
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        String jti = extractJti(token);
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

    @Override
    public boolean isRefreshTokenActive(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }
        Claims claims;
        try {
            claims = extractAllClaims(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
        String type = claims.get("type", String.class);
        String jti = claims.getId();
        if (!TOKEN_TYPE_REFRESH.equals(type) || jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_PREFIX + jti));
    }

    @Override
    public boolean consumeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }
        Claims claims;
        try {
            claims = extractAllClaims(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
        String type = claims.get("type", String.class);
        String jti = claims.getId();
        if (!TOKEN_TYPE_REFRESH.equals(type) || jti == null || jti.isBlank()) {
            return false;
        }

        Long result = redisTemplate.execute(
                CONSUME_REFRESH_SCRIPT,
                List.of(REFRESH_PREFIX + jti),
                USER_REFRESH_PREFIX,
                jti
        );
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String jti;
        try {
            jti = extractJti(refreshToken);
        } catch (ExpiredJwtException e) {
            jti = e.getClaims() != null ? e.getClaims().getId() : null;
        } catch (JwtException | IllegalArgumentException e) {
            return;
        }

        if (jti == null || jti.isBlank()) {
            return;
        }

        String refreshKey = REFRESH_PREFIX + jti;
        String userIdStr = redisTemplate.opsForValue().get(refreshKey);
        redisTemplate.delete(refreshKey);

        if (userIdStr != null && !userIdStr.isBlank()) {
            redisTemplate.opsForSet().remove(USER_REFRESH_PREFIX + userIdStr, jti);
        }
    }

    @Override
    public void revokeAllRefreshTokens(UUID userId) {
        if (userId == null) {
            return;
        }
        String userSetKey = USER_REFRESH_PREFIX + userId;
        Set<String> jtis = redisTemplate.opsForSet().members(userSetKey);
        if (jtis != null && !jtis.isEmpty()) {
            List<String> keysToDelete = jtis.stream()
                    .map(jti -> REFRESH_PREFIX + jti)
                    .toList();
            redisTemplate.delete(keysToDelete);
        }
        redisTemplate.delete(userSetKey);
    }
}
