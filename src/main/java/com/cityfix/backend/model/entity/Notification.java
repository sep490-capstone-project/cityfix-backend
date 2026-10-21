package com.cityfix.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(updatable = false)
    private ZonedDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserNotification> recipients = new ArrayList<>();

    public UserNotification addRecipient(User user) {
        UserNotification recipient = UserNotification.builder()
                .notification(this)
                .user(user)
                .isRead(false)
                .build();
        this.recipients.add(recipient);
        return recipient;
    }
}
