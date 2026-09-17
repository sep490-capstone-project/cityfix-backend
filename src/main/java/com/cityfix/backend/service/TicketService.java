package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface TicketService {
    TicketResponse createTicket(CreateTicketRequest request, String currentUsername);
    void upvoteTicket(UUID ticketId, String currentUsername);
    List<ImageUploadResponse> uploadImage(List<MultipartFile> files, String folder);
    List<DuplicateTicketResponse> findDuplicateTickets(Double latitude, Double longitude, UUID categoryId);
}


