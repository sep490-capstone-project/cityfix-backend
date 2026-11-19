package com.cityfix.backend.model.entity;

import com.cityfix.backend.model.enums.LocationMethod;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

@Entity
@Table(name = "ticket_location")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketLocation {

    @Id
    @Column(name = "ticket_id")
    private UUID ticketId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_method", length = 20)
    private LocationMethod locationMethod;
}
