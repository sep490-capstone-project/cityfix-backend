package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.AiAnalyzeRequest;
import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.AiAnalyzeResponse;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.service.AiService;
import com.cityfix.backend.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final AiService aiService;

    @PostMapping
    // @PreAuthorize("hasAuthority('RESIDENT')") // Tạm tắt JWT
    public ResponseEntity<BaseResponse<TicketResponse>> createTicket(
            @Valid @RequestBody CreateTicketRequest request/*,
            Authentication authentication*/) {
        
        // String email = authentication.getName();
        String email = "trananhduy2205@gmail.com"; // Tạm fix cứng email để test
        TicketResponse response = ticketService.createTicket(request, email);
        return ResponseEntity.ok(BaseResponse.success("Success", response));
    }

    @PostMapping("/{id}/upvote")
    // @PreAuthorize("hasAuthority('RESIDENT')") // Tạm tắt JWT
    public ResponseEntity<BaseResponse<String>> upvoteTicket(
            @PathVariable UUID id/*,
            Authentication authentication*/) {
        
        // String email = authentication.getName();
        String email = "trananhduy2205@gmail.com"; // Tạm fix cứng email để test
        ticketService.upvoteTicket(id, email);
        return ResponseEntity.ok(BaseResponse.success("Upvoted successfully", null));
    }

    @PostMapping("/analyze")
    // @PreAuthorize("hasAuthority('RESIDENT')") // Tạm tắt JWT
    public ResponseEntity<BaseResponse<AiAnalyzeResponse>> analyzeImage(
            @Valid @RequestBody AiAnalyzeRequest request) {
        
        AiAnalyzeResponse response = aiService.analyzeImage(request.getImageUrl());
        return ResponseEntity.ok(BaseResponse.success("Success", response));
    }
}
