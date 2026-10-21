package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {

    @Query(
            value = "SELECT un FROM UserNotification un JOIN FETCH un.notification n LEFT JOIN FETCH n.ticket WHERE un.user.userId = :userId ORDER BY un.createdAt DESC",
            countQuery = "SELECT count(un) FROM UserNotification un WHERE un.user.userId = :userId"
    )
    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query(
            value = "SELECT un FROM UserNotification un JOIN FETCH un.notification n LEFT JOIN FETCH n.ticket WHERE un.user.userId = :userId AND un.isRead = :isRead ORDER BY un.createdAt DESC",
            countQuery = "SELECT count(un) FROM UserNotification un WHERE un.user.userId = :userId AND un.isRead = :isRead"
    )
    Page<UserNotification> findByUserIdAndIsReadOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("isRead") boolean isRead, Pageable pageable);

    long countByUser_UserIdAndIsReadFalse(UUID userId);

    @Query("SELECT un FROM UserNotification un JOIN FETCH un.notification n LEFT JOIN FETCH n.ticket WHERE n.notificationId = :notificationId AND un.user.userId = :userId")
    Optional<UserNotification> findByNotificationIdAndUserId(@Param("notificationId") UUID notificationId, @Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserNotification un SET un.isRead = true, un.readAt = CURRENT_TIMESTAMP WHERE un.user.userId = :userId AND un.isRead = false")
    int markAllAsReadByUserId(@Param("userId") UUID userId);
}
