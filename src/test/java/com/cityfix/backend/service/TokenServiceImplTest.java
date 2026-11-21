package com.cityfix.backend.service;

import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.impl.TokenServiceImpl;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private TokenServiceImpl tokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        String rawSecret = "cityfix-super-secret-jwt-key-for-unit-tests-256-bits-long!";
        String base64Secret = Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(tokenService, "secretKey", base64Secret);
        ReflectionTestUtils.setField(tokenService, "accessTokenExpirationMs", 1_800_000L);   // 30 minutes
        ReflectionTestUtils.setField(tokenService, "refreshTokenExpirationMs", 604_800_000L); // 7 days

        testUser = new User();
        testUser.setUserId(UUID.randomUUID());
        testUser.setEmail("resident@cityfix.com");
        testUser.setRole(new Role(UUID.randomUUID(), "RESIDENT"));
        testUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("generateAccessToken_setsAccessTypeRoleAnd30MinTtl")
    void generateAccessToken_setsAccessTypeRoleAnd30MinTtl() {
        String token = tokenService.generateAccessToken(testUser);

        Claims claims = tokenService.extractAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo("resident@cityfix.com");
        assertThat(claims.get("role", String.class)).isEqualTo("RESIDENT");
        assertThat(claims.get("type", String.class)).isEqualTo(TokenServiceImpl.TOKEN_TYPE_ACCESS);
        assertThat(claims.getId()).isNotBlank();

        long ttlMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(ttlMs).isBetween(1_795_000L, 1_805_000L);
    }

    @Test
    @DisplayName("generateRefreshToken_setsRefreshType7DayTtlAndStoresAtomicallyViaLuaScript")
    @SuppressWarnings("unchecked")
    void generateRefreshToken_setsRefreshType7DayTtlAndStoresAtomicallyViaLuaScript() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);

        String refreshToken = tokenService.generateRefreshToken(testUser);

        Claims claims = tokenService.extractAllClaims(refreshToken);
        String jti = claims.getId();
        String userIdStr = testUser.getUserId().toString();

        assertThat(claims.getSubject()).isEqualTo("resident@cityfix.com");
        assertThat(claims.get("type", String.class)).isEqualTo(TokenServiceImpl.TOKEN_TYPE_REFRESH);
        assertThat(claims.get("role")).isNull();

        long ttlMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(ttlMs).isBetween(604_795_000L, 604_805_000L);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("jwt:refresh:" + jti, "jwt:user-refresh:" + userIdStr)),
                eq(userIdStr),
                eq(jti),
                eq("604800000")
        );
    }

    @Test
    @DisplayName("consumeRefreshToken_activeToken_executesAtomicLuaScriptAndReturnsTrue")
    @SuppressWarnings("unchecked")
    void consumeRefreshToken_activeToken_executesAtomicLuaScriptAndReturnsTrue() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);
        String refreshToken = tokenService.generateRefreshToken(testUser);
        String jti = tokenService.extractJti(refreshToken);

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("jwt:refresh:" + jti)), eq("jwt:user-refresh:"), eq(jti)))
                .thenReturn(1L);

        assertThat(tokenService.consumeRefreshToken(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("consumeRefreshToken_alreadyConsumedByConcurrentRequest_returnsFalse")
    @SuppressWarnings("unchecked")
    void consumeRefreshToken_alreadyConsumedByConcurrentRequest_returnsFalse() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);
        String refreshToken = tokenService.generateRefreshToken(testUser);
        String jti = tokenService.extractJti(refreshToken);

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("jwt:refresh:" + jti)), eq("jwt:user-refresh:"), eq(jti)))
                .thenReturn(0L);

        assertThat(tokenService.consumeRefreshToken(refreshToken)).isFalse();
    }

    @Test
    @DisplayName("consumeRefreshToken_accessTokenPassed_returnsFalseWithoutCallingRedis")
    @SuppressWarnings("unchecked")
    void consumeRefreshToken_accessTokenPassed_returnsFalseWithoutCallingRedis() {
        String accessToken = tokenService.generateAccessToken(testUser);

        assertThat(tokenService.consumeRefreshToken(accessToken)).isFalse();
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    @DisplayName("consumeRefreshToken_redisOutage_propagatesDataAccessExceptionInsteadOfReturningFalse")
    @SuppressWarnings("unchecked")
    void consumeRefreshToken_redisOutage_propagatesDataAccessExceptionInsteadOfReturningFalse() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);
        String refreshToken = tokenService.generateRefreshToken(testUser);
        String jti = tokenService.extractJti(refreshToken);

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("jwt:refresh:" + jti)), eq("jwt:user-refresh:"), eq(jti)))
                .thenThrow(new RedisConnectionFailureException("Redis connection refused"));

        assertThatThrownBy(() -> tokenService.consumeRefreshToken(refreshToken))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("isRefreshTokenActive_validRefreshTokenInRedis_returnsTrue")
    @SuppressWarnings("unchecked")
    void isRefreshTokenActive_validRefreshTokenInRedis_returnsTrue() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);

        String refreshToken = tokenService.generateRefreshToken(testUser);
        String jti = tokenService.extractJti(refreshToken);
        when(redisTemplate.hasKey("jwt:refresh:" + jti)).thenReturn(true);

        assertThat(tokenService.isRefreshTokenActive(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("isRefreshTokenActive_accessTokenPassed_returnsFalse")
    void isRefreshTokenActive_accessTokenPassed_returnsFalse() {
        String accessToken = tokenService.generateAccessToken(testUser);

        assertThat(tokenService.isRefreshTokenActive(accessToken)).isFalse();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("revokeRefreshToken_deletesKeyAndRemovesFromUserSet")
    @SuppressWarnings("unchecked")
    void revokeRefreshToken_deletesKeyAndRemovesFromUserSet() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        String refreshToken = tokenService.generateRefreshToken(testUser);
        String jti = tokenService.extractJti(refreshToken);

        when(valueOperations.get("jwt:refresh:" + jti)).thenReturn(testUser.getUserId().toString());

        tokenService.revokeRefreshToken(refreshToken);

        verify(redisTemplate).delete("jwt:refresh:" + jti);
        verify(setOperations).remove("jwt:user-refresh:" + testUser.getUserId(), jti);
    }

    @Test
    @DisplayName("revokeAllRefreshTokens_deletesAllUserJtisAndSetKey")
    void revokeAllRefreshTokens_deletesAllUserJtisAndSetKey() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        String userSetKey = "jwt:user-refresh:" + testUser.getUserId();
        when(setOperations.members(userSetKey)).thenReturn(Set.of("jti-1", "jti-2"));

        tokenService.revokeAllRefreshTokens(testUser.getUserId());

        verify(redisTemplate).delete(argThat((java.util.Collection<String> keys) ->
                keys != null && keys.contains("jwt:refresh:jti-1") && keys.contains("jwt:refresh:jti-2")));
        verify(redisTemplate).delete(userSetKey);
    }

    @Test
    @DisplayName("blacklistToken_expiredOrMalformedToken_doesNotThrow")
    void blacklistToken_expiredOrMalformedToken_doesNotThrow() {
        tokenService.blacklistToken("not-a-valid-jwt");
        verify(redisTemplate, never()).opsForValue();
    }
}

