package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    Optional<AuthSession> findByRefreshToken(String refreshToken);
    void deleteByUser_UserId(UUID userId);
}
