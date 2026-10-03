package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.ResolutionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ResolutionEvidenceRepository extends JpaRepository<ResolutionEvidence, UUID> {
    
    boolean existsByTicket_TicketId(UUID ticketId);
    
    java.util.List<ResolutionEvidence> findByTicket_TicketId(UUID ticketId);
}
