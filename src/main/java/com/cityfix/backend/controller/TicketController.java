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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKTReader;
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
    @PreAuthorize("hasRole('RESIDENT')")
    public ResponseEntity<BaseResponse<TicketResponse>> createTicket(
            @Valid @RequestBody CreateTicketRequest request) {
        
        TicketResponse response = ticketService.createTicket(request);
        return ResponseEntity.ok(BaseResponse.success("Success", response));
    }

    @PostMapping("/{id}/upvote")
    @PreAuthorize("hasRole('RESIDENT')")
    public ResponseEntity<BaseResponse<String>> upvoteTicket(
            @PathVariable UUID id) {
        
        ticketService.upvoteTicket(id);
        return ResponseEntity.ok(BaseResponse.success("Upvoted successfully", null));
    }

    @PostMapping("/analyze")
    // @PreAuthorize("hasRole('RESIDENT')") // Tạm tắt JWT
    public ResponseEntity<BaseResponse<AiAnalyzeResponse>> analyzeMedia(
            @Valid @RequestBody AiAnalyzeRequest request) {
        
        AiAnalyzeResponse response = aiService.analyzeMedia(request.getMediaUrl());
        return ResponseEntity.ok(BaseResponse.success("Success", response));
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải tệp (ảnh/video) báo cáo sự cố lên Cloudinary", description = "Upload danh sách tệp ảnh/video từ mobile app và nhận về danh sách URL công khai và publicId trên Cloudinary.")
    public ResponseEntity<BaseResponse<List<ImageUploadResponse>>> uploadImage(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "folder", required = false, defaultValue = "cityfix/reports") String folder) {
        
        List<ImageUploadResponse> response = ticketService.uploadImage(files, folder);
        return ResponseEntity.ok(BaseResponse.success("Tải tệp lên thành công.", response));
    }

    @GetMapping("/check-duplicates")
    @Operation(summary = "Kiểm tra vé trùng lặp theo bán kính không gian (PostGIS ST_DWithin)", description = "Tìm các vé đang mở trong bán kính R mét (mặc định 50m) cùng categoryId.")
    public ResponseEntity<BaseResponse<List<DuplicateTicketResponse>>> checkDuplicates(
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam("categoryId") UUID categoryId) {
        
        LocationCoords coords = parseLocation(location, latitude, longitude);

        List<DuplicateTicketResponse> duplicates = ticketService.findDuplicateTickets(coords.latitude(), coords.longitude(), categoryId);
        return ResponseEntity.ok(BaseResponse.success("Kiểm tra vé trùng lặp thành công.", duplicates));
    }

    private LocationCoords parseLocation(String locationStr, Double fallbackLat, Double fallbackLng) {
        if (locationStr != null && !locationStr.isBlank()) {
            String trimmed = locationStr.trim();
            // 1. Try PostGIS HexEWKB format (e.g. 0101000020E61000000D71AC8BDBAC5A40598638D6C58D2540)
            if (trimmed.matches("^[0-9a-fA-F]+$")) {
                try {
                    byte[] bytes = WKBReader.hexToBytes(trimmed);
                    Geometry geom = new WKBReader().read(bytes);
                    if (geom != null && geom.getCoordinate() != null) {
                        return new LocationCoords(geom.getCoordinate().y, geom.getCoordinate().x);
                    }
                } catch (Exception ignored) {
                }
            }

            // 2. Try WKT format (e.g. POINT(106.700806 10.776889))
            if (trimmed.toUpperCase().startsWith("POINT")) {
                try {
                    Geometry geom = new WKTReader().read(trimmed);
                    if (geom != null && geom.getCoordinate() != null) {
                        return new LocationCoords(geom.getCoordinate().y, geom.getCoordinate().x);
                    }
                } catch (Exception ignored) {
                }
            }

            // 3. Try comma-separated format (e.g. "10.776889,106.700806")
            if (trimmed.contains(",")) {
                String[] parts = trimmed.split(",");
                if (parts.length == 2) {
                    try {
                        double lat = Double.parseDouble(parts[0].trim());
                        double lng = Double.parseDouble(parts[1].trim());
                        return new LocationCoords(lat, lng);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return new LocationCoords(fallbackLat, fallbackLng);
    }

    private record LocationCoords(Double latitude, Double longitude) {}
}


