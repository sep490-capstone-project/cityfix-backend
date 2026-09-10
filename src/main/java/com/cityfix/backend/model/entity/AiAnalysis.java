package com.cityfix.backend.model.entity;

import com.cityfix.backend.model.enums.DangerLevel;
import com.cityfix.backend.model.enums.SeverityLevel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "analysis_id")
    private UUID analysisId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    private SeverityLevel severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "danger_level")
    private DangerLevel dangerLevel;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_accepted_by_human")
    private Boolean isAcceptedByHuman;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
