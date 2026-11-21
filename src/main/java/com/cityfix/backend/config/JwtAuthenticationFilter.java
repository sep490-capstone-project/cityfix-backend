package com.cityfix.backend.config;

import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.service.TokenService;
import com.cityfix.backend.service.impl.TokenServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> SKIP_AUTH_PREFIXES = List.of(
            "/api/v1/auth/refresh",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/verify-phone",
            "/api/v1/auth/logout"
    );

    private final TokenService tokenService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        if (path == null) {
            return false;
        }
        for (String prefix : SKIP_AUTH_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + "-")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(response, ErrorCode.UNAUTHENTICATED.name(), "Token xác thực không hợp lệ.");
            return;
        }

        try {
            if (tokenService.isTokenExpired(token)) {
                SecurityContextHolder.clearContext();
                writeUnauthorizedResponse(response, ErrorCode.TOKEN_EXPIRED.name(), ErrorCode.TOKEN_EXPIRED.getMessage());
                return;
            }

            String tokenType = tokenService.extractTokenType(token);
            if (!TokenServiceImpl.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                SecurityContextHolder.clearContext();
                writeUnauthorizedResponse(response, ErrorCode.UNAUTHENTICATED.name(), "Token xác thực không hợp lệ.");
                return;
            }

            if (tokenService.isTokenBlacklisted(token)) {
                SecurityContextHolder.clearContext();
                writeUnauthorizedResponse(response, ErrorCode.UNAUTHENTICATED.name(), "Token đã bị thu hồi hoặc đăng xuất.");
                return;
            }

            User user = tokenService.extractUser(token);
            String role = tokenService.extractRole(token);

            if (user == null || user.getStatus() != UserStatus.ACTIVE || user.isDeleted() || role == null || role.isBlank()) {
                SecurityContextHolder.clearContext();
                writeUnauthorizedResponse(response, ErrorCode.UNAUTHENTICATED.name(), "Tài khoản không hợp lệ hoặc đã bị vô hiệu hóa.");
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(response, ErrorCode.TOKEN_EXPIRED.name(), ErrorCode.TOKEN_EXPIRED.getMessage());
            return;
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(response, ErrorCode.UNAUTHENTICATED.name(), "Token xác thực không hợp lệ.");
            return;
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(response, ErrorCode.UNAUTHENTICATED.name(), ErrorCode.UNAUTHENTICATED.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String errorCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        BaseResponse<Void> body = BaseResponse.error(errorCode, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
