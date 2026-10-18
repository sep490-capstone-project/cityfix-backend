package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.SeverityLevel;
import com.cityfix.backend.model.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    Optional<Ticket> findById(UUID id);

    boolean existsByDescriptionAndStatus(String description, TicketStatus status);

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
        JOIN category c ON c.category_id = :categoryId
        WHERE t.status IN ('INITIATED', 'ASSIGNED', 'IN_PROGRESS')
          AND EXISTS (
              SELECT 1 FROM ticket_subcategory tsc
              JOIN sub_category sc ON tsc.sub_category_id = sc.sub_category_id
              WHERE tsc.ticket_id = t.ticket_id AND sc.category_id = :categoryId
          )
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

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    @Query(value = """
        SELECT t FROM Ticket t
        WHERE t.status = com.cityfix.backend.model.enums.TicketStatus.RESOLVED
          AND EXISTS (
              SELECT 1 FROM TicketStep ts
              WHERE ts.ticket = t
                AND ts.isDeleted = false
                AND ts.status = com.cityfix.backend.model.enums.AssignmentStatus.RESOLVED
          )
          AND NOT EXISTS (
              SELECT 1 FROM TicketStep ts2
              WHERE ts2.ticket = t
                AND ts2.isDeleted = false
                AND ts2.status NOT IN (
                    com.cityfix.backend.model.enums.AssignmentStatus.RESOLVED,
                    com.cityfix.backend.model.enums.AssignmentStatus.COMPLETED,
                    com.cityfix.backend.model.enums.AssignmentStatus.CANCELLED
                )
          )
        """,
        countQuery = """
        SELECT COUNT(t) FROM Ticket t
        WHERE t.status = com.cityfix.backend.model.enums.TicketStatus.RESOLVED
          AND EXISTS (
              SELECT 1 FROM TicketStep ts
              WHERE ts.ticket = t
                AND ts.isDeleted = false
                AND ts.status = com.cityfix.backend.model.enums.AssignmentStatus.RESOLVED
          )
          AND NOT EXISTS (
              SELECT 1 FROM TicketStep ts2
              WHERE ts2.ticket = t
                AND ts2.isDeleted = false
                AND ts2.status NOT IN (
                    com.cityfix.backend.model.enums.AssignmentStatus.RESOLVED,
                    com.cityfix.backend.model.enums.AssignmentStatus.COMPLETED,
                    com.cityfix.backend.model.enums.AssignmentStatus.CANCELLED
                )
          )
        """)
    Page<Ticket> findResolvedTicketsWithAllStepsResolved(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    @Query("SELECT t FROM Ticket t WHERE t.ticketId = :ticketId")
    Optional<Ticket> findByIdWithPessimisticLock(@Param("ticketId") UUID ticketId);

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    @Query(value = "SELECT t FROM Ticket t WHERE t.user.userId = :userId",
           countQuery = "SELECT COUNT(t) FROM Ticket t WHERE t.user.userId = :userId")
    Page<Ticket> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    @Query(value = """
        SELECT t FROM Ticket t
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
                    SELECT 1 FROM RoutingRule rr, TicketSubCategory tsc
                    WHERE tsc.ticket = t
                      AND rr.category = tsc.subCategory.category
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
                    SELECT 1 FROM RoutingRule rr, TicketSubCategory tsc
                    WHERE tsc.ticket = t
                      AND rr.category = tsc.subCategory.category
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

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    @Query(value = """
        SELECT t FROM Ticket t
        WHERE EXISTS (
            SELECT 1 FROM TicketStep ts
            WHERE ts.ticket = t
              AND ts.isDeleted = false
              AND ts.assignee IS NOT NULL
              AND (
                  ts.assignee.department.departmentId IN :departmentIds
                  OR EXISTS (
                      SELECT 1 FROM UserDepartment ud
                      WHERE ud.user = ts.assignee
                        AND ud.department.departmentId IN :departmentIds
                        AND ud.department.isDeleted = false
                  )
              )
        )
        """,
        countQuery = """
        SELECT COUNT(t) FROM Ticket t
        WHERE EXISTS (
            SELECT 1 FROM TicketStep ts
            WHERE ts.ticket = t
              AND ts.isDeleted = false
              AND ts.assignee IS NOT NULL
              AND (
                  ts.assignee.department.departmentId IN :departmentIds
                  OR EXISTS (
                      SELECT 1 FROM UserDepartment ud
                      WHERE ud.user = ts.assignee
                        AND ud.department.departmentId IN :departmentIds
                        AND ud.department.isDeleted = false
                  )
              )
        )
        """)
    Page<Ticket> findTicketsByAssigneeDepartmentIds(
            @Param("departmentIds") java.util.Collection<UUID> departmentIds,
            Pageable pageable
    );
}
