package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;
import com.cityfix.backend.model.enums.Priority;

import com.cityfix.backend.model.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    @Override
    @EntityGraph(attributePaths = {"user"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category", "user"})
    @Query("SELECT DISTINCT t FROM Ticket t WHERE t.ticketId IN :ticketIds")
    List<Ticket> findAllWithSubCategoriesByTicketIdIn(@Param("ticketIds") java.util.Collection<UUID> ticketIds);

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    Optional<Ticket> findById(UUID id);

    boolean existsByDescriptionAndStatus(String description, TicketStatus status);

    @Query("SELECT t.status, COUNT(t) FROM Ticket t GROUP BY t.status")
    List<Object[]> countTicketsGroupByStatus();

    @Query("SELECT COUNT(t) FROM Ticket t WHERE NOT EXISTS (SELECT 1 FROM TicketStep ts WHERE ts.ticket = t AND ts.isDeleted = false AND ts.assignee IS NOT NULL)")
    long countUnassignedTickets();

    long countByNeedsManualReviewTrue();

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.isLifeThreatening = true AND t.status NOT IN :terminalStatuses")
    long countActiveLifeThreateningTickets(@Param("terminalStatuses") java.util.Collection<TicketStatus> terminalStatuses);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.deadline IS NOT NULL AND t.deadline < :now AND t.status NOT IN :closedStatuses")
    long countOverdueTickets(
            @Param("now") java.time.LocalDateTime now,
            @Param("closedStatuses") java.util.Collection<TicketStatus> closedStatuses
    );

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
        JOIN sub_category sc ON sc.sub_category_id = :subCategoryId
        JOIN category c ON c.category_id = sc.category_id
        WHERE t.status IN ('INITIATED', 'ASSIGNED', 'IN_PROGRESS')
          AND EXISTS (
              SELECT 1 FROM ticket_subcategory tsc
              WHERE tsc.ticket_id = t.ticket_id 
                AND tsc.sub_category_id = :subCategoryId
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
            @Param("subCategoryId") UUID subCategoryId
    );

    @Query(value = """
        SELECT COALESCE(MAX(sub.cnt), 0)
        FROM (
            SELECT COUNT(DISTINCT t.ticket_id) AS cnt
            FROM ticket t
            JOIN ticket_subcategory tsc ON tsc.ticket_id = t.ticket_id
            WHERE t.status = 'COMPLETED'
              AND t.created_at >= NOW() - INTERVAL '30 days'
              AND tsc.sub_category_id IN (:subCategoryIds)
              AND ST_DWithin(
                    t.location::geography,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                    10.0
                  )
              AND (:currentTicketId IS NULL OR t.ticket_id != :currentTicketId)
            GROUP BY tsc.sub_category_id
            HAVING COUNT(DISTINCT t.ticket_id) >= 2
        ) sub
        """, nativeQuery = true)
    long countRecurringCompletedTickets(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("subCategoryIds") java.util.Collection<UUID> subCategoryIds,
            @Param("currentTicketId") UUID currentTicketId
    );

    @Query(value = """
        SELECT DISTINCT t.ticket_id
        FROM ticket t
        JOIN ticket_subcategory tsc ON tsc.ticket_id = t.ticket_id
        WHERE t.status = 'COMPLETED'
          AND t.created_at >= NOW() - INTERVAL '30 days'
          AND ST_DWithin(
                t.location::geography,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                10.0
              )
          AND (:currentTicketId IS NULL OR t.ticket_id != :currentTicketId)
          AND tsc.sub_category_id IN (
              SELECT tsc2.sub_category_id
              FROM ticket t2
              JOIN ticket_subcategory tsc2 ON tsc2.ticket_id = t2.ticket_id
              WHERE t2.status = 'COMPLETED'
                AND t2.created_at >= NOW() - INTERVAL '30 days'
                AND tsc2.sub_category_id IN (:subCategoryIds)
                AND ST_DWithin(
                      t2.location::geography,
                      ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                      10.0
                    )
                AND (:currentTicketId IS NULL OR t2.ticket_id != :currentTicketId)
              GROUP BY tsc2.sub_category_id
              HAVING COUNT(DISTINCT t2.ticket_id) >= 2
          )
        """, nativeQuery = true)
    java.util.List<UUID> findRecurringCompletedTicketIds(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("subCategoryIds") java.util.Collection<UUID> subCategoryIds,
            @Param("currentTicketId") UUID currentTicketId
    );

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    @Query(value = """
        SELECT t FROM Ticket t
        WHERE t.status = com.cityfix.backend.model.enums.TicketStatus.INITIATED
          AND EXISTS (
              SELECT 1 FROM RoutingRule rr, TicketSubCategory tsc
              WHERE tsc.ticket = t
                AND rr.category = tsc.subCategory.category
                AND rr.department.departmentId IN :departmentIds
                AND rr.isDeleted = false
                AND (
                    rr.province IS NULL OR TRIM(rr.province) = ''
                    OR (t.address IS NOT NULL AND LOWER(t.address) LIKE LOWER(CONCAT('%', TRIM(rr.province), '%')))
                )
                AND (
                    rr.ward IS NULL OR TRIM(rr.ward) = ''
                    OR (t.address IS NOT NULL AND LOWER(t.address) LIKE LOWER(CONCAT('%', TRIM(rr.ward), '%')))
                )
          )
        """,
        countQuery = """
        SELECT COUNT(t) FROM Ticket t
        WHERE t.status = com.cityfix.backend.model.enums.TicketStatus.INITIATED
          AND EXISTS (
              SELECT 1 FROM RoutingRule rr, TicketSubCategory tsc
              WHERE tsc.ticket = t
                AND rr.category = tsc.subCategory.category
                AND rr.department.departmentId IN :departmentIds
                AND rr.isDeleted = false
                AND (
                    rr.province IS NULL OR TRIM(rr.province) = ''
                    OR (t.address IS NOT NULL AND LOWER(t.address) LIKE LOWER(CONCAT('%', TRIM(rr.province), '%')))
                )
                AND (
                    rr.ward IS NULL OR TRIM(rr.ward) = ''
                    OR (t.address IS NOT NULL AND LOWER(t.address) LIKE LOWER(CONCAT('%', TRIM(rr.ward), '%')))
                )
          )
        """)
    Page<Ticket> findInitiatedTicketsByDepartmentIds(
            @Param("departmentIds") java.util.Collection<UUID> departmentIds,
            Pageable pageable
    );

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

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    @Query(value = """
        SELECT t FROM Ticket t
        WHERE t.status = com.cityfix.backend.model.enums.TicketStatus.RESOLVED
          AND EXISTS (
              SELECT 1 FROM TicketStep ts
              WHERE ts.ticket = t
                AND ts.isDeleted = false
                AND ts.status = com.cityfix.backend.model.enums.AssignmentStatus.RESOLVED
                AND ts.assignee IS NOT NULL
                AND (
                    ts.assignee.department.departmentId IN :departmentIds
                    OR (
                        ts.assignee.department IS NULL
                        AND EXISTS (
                            SELECT 1 FROM UserDepartment ud
                            WHERE ud.user = ts.assignee
                              AND ud.department.departmentId IN :departmentIds
                              AND ud.department.isDeleted = false
                        )
                    )
                )
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
                AND ts.assignee IS NOT NULL
                AND (
                    ts.assignee.department.departmentId IN :departmentIds
                    OR (
                        ts.assignee.department IS NULL
                        AND EXISTS (
                            SELECT 1 FROM UserDepartment ud
                            WHERE ud.user = ts.assignee
                              AND ud.department.departmentId IN :departmentIds
                              AND ud.department.isDeleted = false
                        )
                    )
                )
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
    Page<Ticket> findResolvedTicketsWithAllStepsResolvedByDepartmentIds(
            @Param("departmentIds") java.util.Collection<UUID> departmentIds,
            Pageable pageable
    );

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
    JOIN TicketVote tv ON tv.ticket = t
    WHERE tv.user.userId = :userId
    ORDER BY tv.createdAt DESC
    """,
            countQuery = """
    SELECT COUNT(tv) FROM TicketVote tv
    WHERE tv.user.userId = :userId
    """)
    Page<Ticket> findUpvotedTicketsByUserId(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    @Query(value = """
        SELECT t FROM Ticket t
        WHERE (
            EXISTS (
                SELECT 1 FROM TicketStep ts
                WHERE ts.ticket = t
                  AND ts.isDeleted = false
                  AND ts.assignee IS NOT NULL
                  AND (
                      ts.assignee.department.departmentId = :departmentId
                      OR (
                          ts.assignee.department IS NULL
                          AND EXISTS (
                              SELECT 1 FROM UserDepartment ud
                              WHERE ud.user = ts.assignee
                                AND ud.department.departmentId = :departmentId
                                AND ud.department.isDeleted = false
                          )
                      )
                  )
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
                      AND (
                          rr.province IS NULL OR TRIM(rr.province) = ''
                          OR (t.address IS NOT NULL AND LOWER(t.address) LIKE LOWER(CONCAT('%', TRIM(rr.province), '%')))
                      )
                      AND (
                          rr.ward IS NULL OR TRIM(rr.ward) = ''
                          OR (t.address IS NOT NULL AND LOWER(t.address) LIKE LOWER(CONCAT('%', TRIM(rr.ward), '%')))
                      )
                )
            )
        )
        AND (:priority IS NULL OR t.priority = :priority)
        AND (:status IS NULL OR t.status = :status)
        """,
        countQuery = """
        SELECT COUNT(t) FROM Ticket t
        WHERE (
            EXISTS (
                SELECT 1 FROM TicketStep ts
                WHERE ts.ticket = t
                  AND ts.isDeleted = false
                  AND ts.assignee IS NOT NULL
                  AND (
                      ts.assignee.department.departmentId = :departmentId
                      OR (
                          ts.assignee.department IS NULL
                          AND EXISTS (
                              SELECT 1 FROM UserDepartment ud
                              WHERE ud.user = ts.assignee
                                AND ud.department.departmentId = :departmentId
                                AND ud.department.isDeleted = false
                          )
                      )
                  )
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
                      AND (
                          rr.province IS NULL OR TRIM(rr.province) = ''
                          OR (t.address IS NOT NULL AND LOWER(t.address) LIKE LOWER(CONCAT('%', TRIM(rr.province), '%')))
                      )
                      AND (
                          rr.ward IS NULL OR TRIM(rr.ward) = ''
                          OR (t.address IS NOT NULL AND LOWER(t.address) LIKE LOWER(CONCAT('%', TRIM(rr.ward), '%')))
                      )
                )
            )
        )
        AND (:priority IS NULL OR t.priority = :priority)
        AND (:status IS NULL OR t.status = :status)
        """)
    Page<Ticket> findTicketsByDepartmentAndCriteria(
            @Param("departmentId") UUID departmentId,

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
                  OR (
                      ts.assignee.department IS NULL
                      AND EXISTS (
                          SELECT 1 FROM UserDepartment ud
                          WHERE ud.user = ts.assignee
                            AND ud.department.departmentId IN :departmentIds
                            AND ud.department.isDeleted = false
                      )
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
                  OR (
                      ts.assignee.department IS NULL
                      AND EXISTS (
                          SELECT 1 FROM UserDepartment ud
                          WHERE ud.user = ts.assignee
                            AND ud.department.departmentId IN :departmentIds
                            AND ud.department.isDeleted = false
                      )
                  )
              )
        )
        """)
    Page<Ticket> findTicketsByAssigneeDepartmentIds(
            @Param("departmentIds") java.util.Collection<UUID> departmentIds,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"subCategories.subCategory", "subCategories.subCategory.category"})
    @Query(value = """
        SELECT t FROM Ticket t
        WHERE t.isLifeThreatening = true
          AND t.status IN :statuses
          AND (
              (
                  t.address IS NOT NULL
                  AND (
                      LOWER(t.address) LIKE LOWER(CONCAT('%', :wardPattern, ',%'))
                      OR LOWER(t.address) LIKE LOWER(CONCAT('%', :wardPattern))
                  )
                  AND (:province IS NULL OR :province = '' OR LOWER(t.address) LIKE LOWER(CONCAT('%', :province, '%')))
              )
              OR (
                  t.location IS NOT NULL
                  AND EXISTS (
                      SELECT 1 FROM AdministrativeBoundary ab
                      WHERE ab.ward IS NOT NULL
                        AND (
                            LOWER(ab.ward) = LOWER(:rawWard)
                            OR LOWER(ab.ward) = LOWER(:wardPattern)
                            OR LOWER(ab.ward) = LOWER(CONCAT('Phường ', :wardPattern))
                            OR LOWER(ab.ward) = LOWER(CONCAT('Xã ', :wardPattern))
                            OR LOWER(ab.ward) = LOWER(CONCAT('Thị trấn ', :wardPattern))
                        )
                        AND (:province IS NULL OR :province = '' OR LOWER(ab.province) LIKE LOWER(CONCAT('%', :province, '%')))
                        AND function('ST_Contains', ab.geom, t.location) = true
                  )
              )
          )
        ORDER BY t.createdAt DESC
        """,
        countQuery = """
        SELECT COUNT(t) FROM Ticket t
        WHERE t.isLifeThreatening = true
          AND t.status IN :statuses
          AND (
              (
                  t.address IS NOT NULL
                  AND (
                      LOWER(t.address) LIKE LOWER(CONCAT('%', :wardPattern, ',%'))
                      OR LOWER(t.address) LIKE LOWER(CONCAT('%', :wardPattern))
                  )
                  AND (:province IS NULL OR :province = '' OR LOWER(t.address) LIKE LOWER(CONCAT('%', :province, '%')))
              )
              OR (
                  t.location IS NOT NULL
                  AND EXISTS (
                      SELECT 1 FROM AdministrativeBoundary ab
                      WHERE ab.ward IS NOT NULL
                        AND (
                            LOWER(ab.ward) = LOWER(:rawWard)
                            OR LOWER(ab.ward) = LOWER(:wardPattern)
                            OR LOWER(ab.ward) = LOWER(CONCAT('Phường ', :wardPattern))
                            OR LOWER(ab.ward) = LOWER(CONCAT('Xã ', :wardPattern))
                            OR LOWER(ab.ward) = LOWER(CONCAT('Thị trấn ', :wardPattern))
                        )
                        AND (:province IS NULL OR :province = '' OR LOWER(ab.province) LIKE LOWER(CONCAT('%', :province, '%')))
                        AND function('ST_Contains', ab.geom, t.location) = true
                  )
              )
          )
        """)
    Page<Ticket> findLifeThreateningTicketsByWard(
            @Param("rawWard") String rawWard,
            @Param("wardPattern") String wardPattern,
            @Param("province") String province,
            @Param("statuses") java.util.Collection<TicketStatus> statuses,
            Pageable pageable
    );
}
