package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.TicketSubCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketSubCategoryRepository extends JpaRepository<TicketSubCategory, UUID> {
    List<TicketSubCategory> findByTicket_TicketId(UUID ticketId);
    void deleteByTicket_TicketId(UUID ticketId);
}
