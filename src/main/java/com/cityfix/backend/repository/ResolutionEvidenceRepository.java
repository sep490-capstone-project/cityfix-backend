package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.ResolutionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ResolutionEvidenceRepository extends JpaRepository<ResolutionEvidence, UUID> {
    
    boolean existsByTicketStep_Id(UUID ticketStepId);
    
    java.util.List<ResolutionEvidence> findByTicketStep_Id(UUID ticketStepId);

    java.util.List<ResolutionEvidence> findByTicketStep_Ticket_TicketId(UUID ticketId);
}
