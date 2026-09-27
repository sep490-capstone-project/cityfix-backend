package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @Query(value = """
        SELECT t.ticket_id AS ticketId,
               t.description AS description,
               t.address AS address,
               t.status AS status,
               c.name AS categoryName,
               ST_Y(t.location) AS latitude,
               ST_X(t.location) AS longitude,
               ST_Distance(t.location::geography, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography) AS distanceMeters,
               t.created_at AS createdAt
        FROM ticket t
        LEFT JOIN category c ON t.category_id = c.category_id
        WHERE t.category_id = :categoryId
          AND t.status IN ('INITIATED', 'ASSIGNED', 'IN_PROGRESS')
          AND ST_DWithin(
                t.location::geography,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                50.0
              )
        ORDER BY distanceMeters ASC
        """, nativeQuery = true)
    List<DuplicateTicketProjection> findDuplicateTickets(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("categoryId") UUID categoryId
    );

    org.springframework.data.domain.Page<Ticket> findByStatus(com.cityfix.backend.model.enums.TicketStatus status, org.springframework.data.domain.Pageable pageable);
}
