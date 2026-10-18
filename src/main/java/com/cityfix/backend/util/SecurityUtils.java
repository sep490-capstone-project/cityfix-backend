package com.cityfix.backend.util;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.entity.User;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
        // Private constructor for utility class
    }

    public static Authentication getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return auth;
    }

    public static String getCurrentUserEmail() {
        Authentication auth = getAuthentication();
        Object principal = auth.getPrincipal();

        if (principal instanceof User user && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        if (principal instanceof UserDetails userDetails && userDetails.getUsername() != null && !userDetails.getUsername().isBlank()) {
            return userDetails.getUsername();
        }
        if (principal instanceof String str && !str.isBlank() && !"anonymousUser".equalsIgnoreCase(str)) {
            return str;
        }
        if (principal instanceof Principal p && p.getName() != null && !p.getName().isBlank() && !"anonymousUser".equalsIgnoreCase(p.getName())) {
            return p.getName();
        }
        String name = auth.getName();
        if (name != null && !name.isBlank() && !"anonymousUser".equalsIgnoreCase(name)) {
            return name;
        }

        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    public static Optional<User> getCurrentUserPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
