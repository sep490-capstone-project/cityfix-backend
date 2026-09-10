package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.TicketVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TicketVoteRepository extends JpaRepository<TicketVote, UUID> {
    boolean existsByTicket_TicketIdAndUser_UserId(UUID ticketId, UUID userId);
}
