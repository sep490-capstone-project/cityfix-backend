package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.TicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketHistoryRepository extends JpaRepository<TicketHistory, UUID> {

    List<TicketHistory> findByTicketTicketIdOrderByCreatedAtDesc(UUID ticketId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT th.actor FROM TicketHistory th " +
           "WHERE th.ticket.ticketId IN :ticketIds " +
           "AND th.actor IS NOT NULL " +
           "AND UPPER(th.actor.role.roleName) = 'MANAGER' " +
           "AND th.actor.status = com.cityfix.backend.model.enums.UserStatus.ACTIVE " +
           "AND th.actor.isDeleted = false")
    List<com.cityfix.backend.model.entity.User> findActiveManagersByTicketIds(@org.springframework.data.repository.query.Param("ticketIds") java.util.Collection<UUID> ticketIds);
}
