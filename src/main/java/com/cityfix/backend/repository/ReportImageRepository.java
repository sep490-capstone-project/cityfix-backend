package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.ReportImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportImageRepository extends JpaRepository<ReportImage, UUID> {
    List<ReportImage> findByTicket_TicketId(UUID ticketId);
}
