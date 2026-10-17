package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.TicketVote;
import com.cityfix.backend.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketVoteRepository extends JpaRepository<TicketVote, UUID> {
    boolean existsByTicket_TicketIdAndUser_UserId(UUID ticketId, UUID userId);

    @Query("SELECT DISTINCT tv.user FROM TicketVote tv WHERE tv.ticket.ticketId = :ticketId AND tv.user.isDeleted = false AND tv.user.status = com.cityfix.backend.model.enums.UserStatus.ACTIVE")
    List<User> findUpvotersByTicketId(@Param("ticketId") UUID ticketId);
}
