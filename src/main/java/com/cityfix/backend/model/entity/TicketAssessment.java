package com.cityfix.backend.model.entity;

import com.cityfix.backend.model.enums.Priority;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_assessment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketAssessment {

    @Id
    @Column(name = "ticket_id")
    private UUID ticketId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private Priority priority;

    @Column(name = "priority_reasoning", columnDefinition = "TEXT")
    private String priorityReasoning;

    @Column(name = "is_life_threatening")
    private Boolean isLifeThreatening;

    @Column(name = "needs_manual_review")
    private Boolean needsManualReview;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "deadline")
    private LocalDateTime deadline;
}
