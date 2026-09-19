package com.cityfix.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ticket_id")
    private UUID ticketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private com.cityfix.backend.model.enums.SeverityLevel severity;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "danger_level")
    private com.cityfix.backend.model.enums.DangerLevel dangerLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_method")
    private com.cityfix.backend.model.enums.LocationMethod locationMethod;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point location;

    private String address;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.cityfix.backend.model.enums.TicketStatus status = com.cityfix.backend.model.enums.TicketStatus.INITIATED;

    @Builder.Default
    @Column(name = "is_life_threatening", nullable = false)
    private boolean isLifeThreatening = false;

    @Builder.Default
    @Column(name = "needs_manual_review", nullable = false)
    private boolean needsManualReview = false;
}
