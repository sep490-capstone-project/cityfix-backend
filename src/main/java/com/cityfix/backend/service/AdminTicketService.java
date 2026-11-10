package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.AdminTicketFilterRequest;
import com.cityfix.backend.model.dto.response.AdminTicketDetailResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminTicketService {

    Page<TicketResponse> getAllTickets(AdminTicketFilterRequest filter, Pageable pageable);

    AdminTicketDetailResponse getTicketDetail(UUID ticketId);
}
