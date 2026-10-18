package com.cityfix.backend.util;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        String email = SecurityUtils.getCurrentUserEmail();
        assertThat(email).isEqualTo("staff@cityfix.com");
    }

    @Test
    @DisplayName("getCurrentUserEmail_whenPrincipalIsUserDetails_returnsUsername")
    void getCurrentUserEmail_whenPrincipalIsUserDetails_returnsUsername() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("manager@cityfix.com");

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        String email = SecurityUtils.getCurrentUserEmail();
        assertThat(email).isEqualTo("manager@cityfix.com");
    }

    @Test
    @DisplayName("getCurrentUserEmail_whenPrincipalIsStringEmail_returnsString")
    void getCurrentUserEmail_whenPrincipalIsStringEmail_returnsString() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("resident@cityfix.com", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        String email = SecurityUtils.getCurrentUserEmail();
        assertThat(email).isEqualTo("resident@cityfix.com");
    }

    @Test
    @DisplayName("getCurrentUserEmail_whenPrincipalIsCustomPrincipal_returnsPrincipalName")
    void getCurrentUserEmail_whenPrincipalIsCustomPrincipal_returnsPrincipalName() {
        Principal principal = () -> "admin@cityfix.com";
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
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
        User user = User.builder().userId(UUID.randomUUID()).email("staff@cityfix.com").build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<User> principal = SecurityUtils.getCurrentUserPrincipal();
        assertThat(principal).isPresent().contains(user);
    }

    @Test
    @DisplayName("getCurrentUserPrincipal_whenPrincipalIsNotUser_returnsEmpty")
    void getCurrentUserPrincipal_whenPrincipalIsNotUser_returnsEmpty() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("string_principal", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<User> principal = SecurityUtils.getCurrentUserPrincipal();
        assertThat(principal).isEmpty();
    }
}
