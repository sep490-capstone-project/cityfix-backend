package com.cityfix.backend.config;

import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.service.TokenService;
import com.cityfix.backend.service.impl.TokenServiceImpl;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private User activeUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        activeUser = new User();
        activeUser.setUserId(UUID.randomUUID());
        activeUser.setEmail("resident@cityfix.com");
        activeUser.setRole(new Role(UUID.randomUUID(), "RESIDENT"));
        activeUser.setStatus(UserStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("shouldNotFilter_publicAuthEndpoints_returnsTrueToPrevent401Loop")
    void shouldNotFilter_publicAuthEndpoints_returnsTrueToPrevent401Loop() {
        for (String path : new String[]{
                "/api/v1/auth/refresh",
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/reset-password",
                "/api/v1/auth/verify-phone",
                "/api/v1/auth/verify-phone-firebase",
                "/api/v1/auth/logout"
        }) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
            req.setServletPath(path);
            assertThat(filter.shouldNotFilter(req)).as("Path %s should be skipped", path).isTrue();
        }

        MockHttpServletRequest protectedReq = new MockHttpServletRequest("GET", "/api/v1/tickets/top-upvoted");
        protectedReq.setServletPath("/api/v1/tickets/top-upvoted");
        assertThat(filter.shouldNotFilter(protectedReq)).isFalse();
    }

    @Test
    @DisplayName("doFilterInternal_expiredJwtException_returns401WithTokenExpiredJson")
    void doFilterInternal_expiredJwtException_returns401WithTokenExpiredJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets/top-upvoted");
        request.setServletPath("/api/v1/tickets/top-upvoted");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenService.isTokenExpired("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "JWT expired"));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"success\":false");
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"TOKEN_EXPIRED\"");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("doFilterInternal_refreshTokenUsedAsBearer_returns401Unauthenticated")
    void doFilterInternal_refreshTokenUsedAsBearer_returns401Unauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets/top-upvoted");
        request.setServletPath("/api/v1/tickets/top-upvoted");
        request.addHeader("Authorization", "Bearer refresh-token-xyz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenService.isTokenExpired("refresh-token-xyz")).thenReturn(false);
        when(tokenService.extractTokenType("refresh-token-xyz")).thenReturn(TokenServiceImpl.TOKEN_TYPE_REFRESH);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"UNAUTHENTICATED\"");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("doFilterInternal_blacklistedAccessToken_returns401Unauthenticated")
    void doFilterInternal_blacklistedAccessToken_returns401Unauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profile/me");
        request.setServletPath("/api/v1/profile/me");
        request.addHeader("Authorization", "Bearer blacklisted-access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenService.isTokenExpired("blacklisted-access-token")).thenReturn(false);
        when(tokenService.extractTokenType("blacklisted-access-token")).thenReturn(TokenServiceImpl.TOKEN_TYPE_ACCESS);
        when(tokenService.isTokenBlacklisted("blacklisted-access-token")).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"UNAUTHENTICATED\"");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("doFilterInternal_noAuthorizationHeader_passesThroughFilterChain")
    void doFilterInternal_noAuthorizationHeader_passesThroughFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets/check-duplicates");
        request.setServletPath("/api/v1/tickets/check-duplicates");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal_validAccessToken_setsAuthenticationAndProceeds")
    void doFilterInternal_validAccessToken_setsAuthenticationAndProceeds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets/top-upvoted");
        request.setServletPath("/api/v1/tickets/top-upvoted");
        request.addHeader("Authorization", "Bearer valid-access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenService.isTokenExpired("valid-access-token")).thenReturn(false);
        when(tokenService.extractTokenType("valid-access-token")).thenReturn(TokenServiceImpl.TOKEN_TYPE_ACCESS);
        when(tokenService.isTokenBlacklisted("valid-access-token")).thenReturn(false);
        when(tokenService.extractUser("valid-access-token")).thenReturn(activeUser);
        when(tokenService.extractRole("valid-access-token")).thenReturn("RESIDENT");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(activeUser);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_RESIDENT");
        verify(filterChain, times(1)).doFilter(request, response);
    }
}

