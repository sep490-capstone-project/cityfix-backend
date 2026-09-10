package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.TicketResponse;

import java.util.UUID;

public interface TicketService {
    TicketResponse createTicket(CreateTicketRequest request, String currentUsername);
    void upvoteTicket(UUID ticketId, String currentUsername);
}
