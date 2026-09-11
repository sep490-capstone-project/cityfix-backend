package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface TicketService {
    TicketResponse createTicket(CreateTicketRequest request, String currentUsername);
    void upvoteTicket(UUID ticketId, String currentUsername);
    ImageUploadResponse uploadImage(MultipartFile file, String folder);
}

