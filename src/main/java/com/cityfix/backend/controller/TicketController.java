package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.AiAnalyzeRequest;
import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.AiAnalyzeResponse;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.service.AiService;
import com.cityfix.backend.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final AiService aiService;

    @PostMapping
    @PreAuthorize("hasAuthority('RESIDENT')")
    public ResponseEntity<BaseResponse<TicketResponse>> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            Authentication authentication) {
        
        String email = authentication.getName();
        TicketResponse response = ticketService.createTicket(request, email);
        return ResponseEntity.ok(BaseResponse.success("Success", response));
    }

    @PostMapping("/{id}/upvote")
    @PreAuthorize("hasAuthority('RESIDENT')")
    public ResponseEntity<BaseResponse<String>> upvoteTicket(
            @PathVariable UUID id,
            Authentication authentication) {
        
        String email = authentication.getName();
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

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải ảnh báo cáo sự cố lên Cloudinary", description = "Upload file ảnh từ mobile app và nhận về URL công khai và publicId trên Cloudinary.")
    public ResponseEntity<BaseResponse<ImageUploadResponse>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "cityfix/reports") String folder) {
        
        ImageUploadResponse response = ticketService.uploadImage(file, folder);
        return ResponseEntity.ok(BaseResponse.success("Tải ảnh lên thành công.", response));
    }

    @GetMapping("/check-duplicates")
    @Operation(summary = "Kiểm tra vé trùng lặp theo bán kính không gian (PostGIS ST_DWithin)", description = "Tìm các vé đang mở trong bán kính R mét (mặc định 50m) cùng categoryId.")
    public ResponseEntity<BaseResponse<List<DuplicateTicketResponse>>> checkDuplicates(
            @RequestParam("latitude") Double latitude,
            @RequestParam("longitude") Double longitude,
            @RequestParam("categoryId") UUID categoryId) {
        
        List<DuplicateTicketResponse> duplicates = ticketService.findDuplicateTickets(latitude, longitude, categoryId);
        return ResponseEntity.ok(BaseResponse.success("Kiểm tra vé trùng lặp thành công.", duplicates));
    }
}


