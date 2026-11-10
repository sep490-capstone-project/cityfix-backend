package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RatingRepository extends JpaRepository<Rating, UUID> {

    boolean existsByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(UUID ticketId, UUID userId);

    Optional<Rating> findByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(UUID ticketId, UUID userId);

    List<Rating> findByTicket_TicketIdAndIsDeletedFalse(UUID ticketId);

    Page<Rating> findByTicket_TicketIdAndIsDeletedFalse(UUID ticketId, Pageable pageable);

    Optional<Rating> findByIdAndTicket_TicketIdAndIsDeletedFalse(UUID ratingId, UUID ticketId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(AVG(r.rate), 0.0) FROM Rating r WHERE r.isDeleted = false")
    Double findAverageActiveRating();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"ticket"})
    List<Rating> findByTicket_TicketIdInAndIsDeletedFalse(java.util.Collection<UUID> ticketIds);
}
