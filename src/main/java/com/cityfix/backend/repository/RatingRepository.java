package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.Rating;
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
}
