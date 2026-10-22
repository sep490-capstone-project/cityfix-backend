package com.cityfix.backend.model.entity;

import com.cityfix.backend.model.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "ticket_step")
@SQLDelete(sql = "UPDATE ticket_step SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id", nullable = false)
    private Step step;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @BatchSize(size = 20)
    @Builder.Default
    @OneToMany(mappedBy = "ticketStep", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResolutionEvidence> evidences = new ArrayList<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_status", nullable = false, length = 20)
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "ticket_step_dependency",
        joinColumns = @JoinColumn(name = "step_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "depends_on_step_id", referencedColumnName = "id")
    )
    @Builder.Default
    private Set<TicketStep> dependsOn = new HashSet<>();

    @ManyToMany(mappedBy = "dependsOn", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TicketStep> dependentSteps = new HashSet<>();


    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
