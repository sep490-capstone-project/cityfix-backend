package com.cityfix.backend.service;

import com.cityfix.backend.model.entity.User;
import io.jsonwebtoken.Claims;

import java.util.Date;
import java.util.function.Function;

public interface TokenService {
    String generateAccessToken(User user);
    String generateRefreshToken(User user);
    Claims extractAllClaims(String token);
    User extractUser(String token);
    boolean isTokenExpired(String token);
    Date extractExpiration(String token);
    String extractRole(String token);
    String extractJti(String token);
    boolean isTokenBlacklisted(String token);
    void blacklistToken(String token);
    <T> T extractClaim(String token, Function<Claims, T> resolver);
}
