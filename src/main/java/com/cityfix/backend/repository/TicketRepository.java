package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.SeverityLevel;
import com.cityfix.backend.model.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
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
               (SELECT ri.image_url FROM report_image ri WHERE ri.ticket_id = t.ticket_id ORDER BY ri.created_at ASC LIMIT 1) AS imageUrl,
               t.created_at AS createdAt
        FROM ticket t
        LEFT JOIN category c ON t.category_id = c.category_id
        WHERE t.category_id = :categoryId
          AND t.status IN ('INITIATED', 'OPEN', 'ASSIGNED', 'IN_PROGRESS')
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

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.ticketId = :ticketId")
    Optional<Ticket> findByIdWithPessimisticLock(@Param("ticketId") UUID ticketId);

    @Query(value = "SELECT t FROM Ticket t LEFT JOIN FETCH t.category WHERE t.user.userId = :userId",
           countQuery = "SELECT COUNT(t) FROM Ticket t WHERE t.user.userId = :userId")
    Page<Ticket> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
        SELECT t FROM Ticket t
        LEFT JOIN FETCH t.category
        WHERE (
            EXISTS (
                SELECT 1 FROM TicketStep ts
                WHERE ts.ticket = t
                  AND ts.assignee.department.departmentId = :departmentId
                  AND ts.isDeleted = false
            )
            OR (
                NOT EXISTS (
                    SELECT 1 FROM TicketStep ts2
                    WHERE ts2.ticket = t
                      AND ts2.assignee IS NOT NULL
                      AND ts2.isDeleted = false
                )
                AND EXISTS (
                    SELECT 1 FROM RoutingRule rr
                    WHERE rr.category = t.category
                      AND rr.department.departmentId = :departmentId
                      AND rr.isDeleted = false
                )
            )
        )
        AND (:severity IS NULL OR t.severity = :severity)
        AND (:priority IS NULL OR t.priority = :priority)
        AND (:status IS NULL OR t.status = :status)
        """,
        countQuery = """
        SELECT COUNT(t) FROM Ticket t
        WHERE (
            EXISTS (
                SELECT 1 FROM TicketStep ts
                WHERE ts.ticket = t
                  AND ts.assignee.department.departmentId = :departmentId
                  AND ts.isDeleted = false
            )
            OR (
                NOT EXISTS (
                    SELECT 1 FROM TicketStep ts2
                    WHERE ts2.ticket = t
                      AND ts2.assignee IS NOT NULL
                      AND ts2.isDeleted = false
                )
                AND EXISTS (
                    SELECT 1 FROM RoutingRule rr
                    WHERE rr.category = t.category
                      AND rr.department.departmentId = :departmentId
                      AND rr.isDeleted = false
                )
            )
        )
        AND (:severity IS NULL OR t.severity = :severity)
        AND (:priority IS NULL OR t.priority = :priority)
        AND (:status IS NULL OR t.status = :status)
        """)
    Page<Ticket> findTicketsByDepartmentAndCriteria(
            @Param("departmentId") UUID departmentId,
            @Param("severity") SeverityLevel severity,
            @Param("priority") Priority priority,
            @Param("status") TicketStatus status,
            Pageable pageable
    );
}
