package com.cityfix.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "resolution_evidence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolutionEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "evidence_id")
    private UUID evidenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_step_id")
    private TicketStep ticketStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
