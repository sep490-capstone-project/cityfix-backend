package com.cityfix.backend.util;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getCurrentUserEmail_whenPrincipalIsUser_returnsUserEmail")
    void getCurrentUserEmail_whenPrincipalIsUser_returnsUserEmail() {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("staff@cityfix.com")
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        String email = SecurityUtils.getCurrentUserEmail();
        assertThat(email).isEqualTo("staff@cityfix.com");
    }

    @Test
    @DisplayName("getCurrentUserEmail_whenPrincipalIsUserDetails_returnsUsername")
    void getCurrentUserEmail_whenPrincipalIsUserDetails_returnsUsername() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("manager@cityfix.com");

        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        String email = SecurityUtils.getCurrentUserEmail();
        assertThat(email).isEqualTo("manager@cityfix.com");
    }

    @Test
    @DisplayName("getCurrentUserEmail_whenPrincipalIsStringEmail_returnsString")
    void getCurrentUserEmail_whenPrincipalIsStringEmail_returnsString() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "resident@cityfix.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_RESIDENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        String email = SecurityUtils.getCurrentUserEmail();
        assertThat(email).isEqualTo("resident@cityfix.com");
    }

    @Test
    @DisplayName("getCurrentUserEmail_whenPrincipalIsCustomPrincipal_returnsPrincipalName")
    void getCurrentUserEmail_whenPrincipalIsCustomPrincipal_returnsPrincipalName() {
        Principal principal = () -> "admin@cityfix.com";
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        String email = SecurityUtils.getCurrentUserEmail();
        assertThat(email).isEqualTo("admin@cityfix.com");
    }

    @Test
    @DisplayName("getCurrentUserEmail_whenNoAuthentication_throwsUNAUTHENTICATED")
    void getCurrentUserEmail_whenNoAuthentication_throwsUNAUTHENTICATED() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(SecurityUtils::getCurrentUserEmail)
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("getCurrentUserEmail_whenAnonymousAuthenticationToken_throwsUNAUTHENTICATED")
    void getCurrentUserEmail_whenAnonymousAuthenticationToken_throwsUNAUTHENTICATED() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        assertThatThrownBy(SecurityUtils::getCurrentUserEmail)
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("getCurrentUserPrincipal_whenPrincipalIsUser_returnsOptionalUser")
    void getCurrentUserPrincipal_whenPrincipalIsUser_returnsOptionalUser() {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("staff@cityfix.com")
                .build();

        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                user,
                "password",
                "ROLE_STAFF"
        );
        auth.setAuthenticated(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        Optional<User> result = SecurityUtils.getCurrentUserPrincipal();

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("staff@cityfix.com");
    }

    @Test
    @DisplayName("getCurrentUserPrincipal_whenPrincipalIsNotUser_returnsEmpty")
    void getCurrentUserPrincipal_whenPrincipalIsNotUser_returnsEmpty() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "string_principal",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<User> principal = SecurityUtils.getCurrentUserPrincipal();
        assertThat(principal).isEmpty();
    }

    @Test
    @DisplayName("getCurrentUserPrincipal_whenUnauthenticated_returnsEmpty")
    void getCurrentUserPrincipal_whenUnauthenticated_returnsEmpty() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<User> principal = SecurityUtils.getCurrentUserPrincipal();
        assertThat(principal).isEmpty();
    }
}
