package com.cityfix.backend.model.entity;

import com.cityfix.backend.model.enums.LocationMethod;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ticket")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ticket_id")
    private UUID ticketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private TicketLocation ticketLocation;

    @OneToOne(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private TicketAssessment ticketAssessment;

    @BatchSize(size = 20)
    @Builder.Default
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketSubCategory> subCategories = new ArrayList<>();

    @BatchSize(size = 20)
    @Builder.Default
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<TicketStep> steps = new ArrayList<>();

    @BatchSize(size = 20)
    @Builder.Default
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportImage> images = new ArrayList<>();

    @Transient
    private String imageUrl;

    @BatchSize(size = 20)
    @Builder.Default
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Rating> ratings = new ArrayList<>();

    @BatchSize(size = 20)
    @Builder.Default
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Notification> notifications = new ArrayList<>();

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.INITIATED;

    @Builder.Default
    @Column(name = "is_anonymous", nullable = false)
    private Boolean isAnonymous = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        syncSatelliteEntities();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        syncSatelliteEntities();
    }

    private void syncSatelliteEntities() {
        if (ticketLocation == null) {
            ticketLocation = new TicketLocation();
        }
        ticketLocation.setTicket(this);

        if (ticketAssessment == null) {
            ticketAssessment = new TicketAssessment();
        }
        ticketAssessment.setTicket(this);
    }

    public void setTicketLocation(TicketLocation ticketLocation) {
        this.ticketLocation = ticketLocation;
        if (ticketLocation != null && ticketLocation.getTicket() != this) {
            ticketLocation.setTicket(this);
        }
    }

    public void setTicketAssessment(TicketAssessment ticketAssessment) {
        this.ticketAssessment = ticketAssessment;
        if (ticketAssessment != null && ticketAssessment.getTicket() != this) {
            ticketAssessment.setTicket(this);
        }
    }

    private TicketLocation ensureTicketLocation() {
        if (this.ticketLocation == null) {
            this.ticketLocation = new TicketLocation();
            this.ticketLocation.setTicket(this);
        } else if (this.ticketLocation.getTicket() != this) {
            this.ticketLocation.setTicket(this);
        }
        return this.ticketLocation;
    }

    private TicketAssessment ensureTicketAssessment() {
        if (this.ticketAssessment == null) {
            this.ticketAssessment = new TicketAssessment();
            this.ticketAssessment.setTicket(this);
        } else if (this.ticketAssessment.getTicket() != this) {
            this.ticketAssessment.setTicket(this);
        }
        return this.ticketAssessment;
    }

    // ─── Delegating Getters & Setters for TicketLocation ──────────────────────

    public Point getLocation() {
        return ticketLocation != null ? ticketLocation.getLocation() : null;
    }

    public void setLocation(Point location) {
        ensureTicketLocation().setLocation(location);
    }

    public String getAddress() {
        return ticketLocation != null ? ticketLocation.getAddress() : null;
    }

    public void setAddress(String address) {
        ensureTicketLocation().setAddress(address);
    }

    public LocationMethod getLocationMethod() {
        return ticketLocation != null ? ticketLocation.getLocationMethod() : null;
    }

    public void setLocationMethod(LocationMethod locationMethod) {
        ensureTicketLocation().setLocationMethod(locationMethod);
    }

    // ─── Delegating Getters & Setters for TicketAssessment ────────────────────

    public Priority getPriority() {
        return ticketAssessment != null ? ticketAssessment.getPriority() : null;
    }

    public void setPriority(Priority priority) {
        ensureTicketAssessment().setPriority(priority);
    }

    public String getPriorityReasoning() {
        return ticketAssessment != null ? ticketAssessment.getPriorityReasoning() : null;
    }

    public void setPriorityReasoning(String priorityReasoning) {
        ensureTicketAssessment().setPriorityReasoning(priorityReasoning);
    }

    public Boolean getIsLifeThreatening() {
        return ticketAssessment != null ? ticketAssessment.getIsLifeThreatening() : null;
    }

    public void setIsLifeThreatening(Boolean isLifeThreatening) {
        ensureTicketAssessment().setIsLifeThreatening(isLifeThreatening);
    }

    public Boolean getNeedsManualReview() {
        return ticketAssessment != null ? ticketAssessment.getNeedsManualReview() : null;
    }

    public void setNeedsManualReview(Boolean needsManualReview) {
        ensureTicketAssessment().setNeedsManualReview(needsManualReview);
    }

    public String getReviewNote() {
        return ticketAssessment != null ? ticketAssessment.getReviewNote() : null;
    }

    public void setReviewNote(String reviewNote) {
        ensureTicketAssessment().setReviewNote(reviewNote);
    }

    public LocalDateTime getDeadline() {
        return ticketAssessment != null ? ticketAssessment.getDeadline() : null;
    }

    public void setDeadline(LocalDateTime deadline) {
        ensureTicketAssessment().setDeadline(deadline);
    }

    public List<ReportImage> getReportImages() {
        return this.images;
    }

    public void setReportImages(List<ReportImage> reportImages) {
        this.images = reportImages;
    }

    public static class TicketBuilder {
        private TicketLocation ensureBuilderLocation() {
            if (this.ticketLocation == null) {
                this.ticketLocation = new TicketLocation();
            }
            return this.ticketLocation;
        }

        private TicketAssessment ensureBuilderAssessment() {
            if (this.ticketAssessment == null) {
                this.ticketAssessment = new TicketAssessment();
            }
            return this.ticketAssessment;
        }

        public TicketBuilder location(Point location) {
            ensureBuilderLocation().setLocation(location);
            return this;
        }

        public TicketBuilder address(String address) {
            ensureBuilderLocation().setAddress(address);
            return this;
        }

        public TicketBuilder locationMethod(LocationMethod locationMethod) {
            ensureBuilderLocation().setLocationMethod(locationMethod);
            return this;
        }

        public TicketBuilder priority(Priority priority) {
            ensureBuilderAssessment().setPriority(priority);
            return this;
        }

        public TicketBuilder priorityReasoning(String priorityReasoning) {
            ensureBuilderAssessment().setPriorityReasoning(priorityReasoning);
            return this;
        }

        public TicketBuilder isLifeThreatening(Boolean isLifeThreatening) {
            ensureBuilderAssessment().setIsLifeThreatening(isLifeThreatening);
            return this;
        }

        public TicketBuilder needsManualReview(Boolean needsManualReview) {
            ensureBuilderAssessment().setNeedsManualReview(needsManualReview);
            return this;
        }

        public TicketBuilder reviewNote(String reviewNote) {
            ensureBuilderAssessment().setReviewNote(reviewNote);
            return this;
        }

        public TicketBuilder deadline(LocalDateTime deadline) {
            ensureBuilderAssessment().setDeadline(deadline);
            return this;
        }

        public Ticket build() {
            Ticket ticket = new Ticket(
                    this.ticketId,
                    this.user,
                    this.ticketLocation,
                    this.ticketAssessment,
                    this.subCategories$set ? this.subCategories$value : new ArrayList<>(),
                    this.steps$set ? this.steps$value : new ArrayList<>(),
                    this.images$set ? this.images$value : new ArrayList<>(),
                    this.imageUrl,
                    this.ratings$set ? this.ratings$value : new ArrayList<>(),
                    this.notifications$set ? this.notifications$value : new ArrayList<>(),
                    this.description,
                    this.status$set && this.status$value != null ? this.status$value : TicketStatus.INITIATED,
                    this.isAnonymous$set && this.isAnonymous$value != null ? this.isAnonymous$value : Boolean.FALSE,
                    this.createdAt,
                    this.updatedAt
            );
            if (ticket.ticketLocation != null) {
                ticket.ticketLocation.setTicket(ticket);
            }
            if (ticket.ticketAssessment != null) {
                ticket.ticketAssessment.setTicket(ticket);
            }
            return ticket;
        }
    }
}
