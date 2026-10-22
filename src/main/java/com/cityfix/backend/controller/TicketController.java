package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.AiAnalyzeRequest;
import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.AiAnalyzeResponse;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.service.AiService;
import com.cityfix.backend.service.ManagerTicketService;
import com.cityfix.backend.service.TicketService;
import com.cityfix.backend.service.TicketStepService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKTReader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    private final ManagerTicketService managerTicketService;
    private final TicketStepService ticketStepService;
    private final AiService aiService;

    @GetMapping("/my-reports")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách các vé do người dùng hiện tại đã báo cáo", description = "Trích xuất thông tin người dùng từ SecurityContext, trả về danh sách phân trang các phản ánh đã tạo.")
    public ResponseEntity<BaseResponse<Page<TicketResponse>>> getMyReportedTickets(
            @PageableDefault(size = 10) Pageable pageable) {
        Page<TicketResponse> response = ticketService.getMyReportedTickets(pageable);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách phản ánh thành công.", response));
    }

    @GetMapping("/{ticketId}/steps")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER')")
    @Operation(summary = "Lấy danh sách các bước xử lý của vé", description = "Chỉ cho phép vai trò STAFF và MANAGER. Chặn ADMIN và các vai trò khác.")
    public ResponseEntity<BaseResponse<List<TicketStepResponse>>> getTicketSteps(@PathVariable UUID ticketId) {
        List<TicketStepResponse> response = ticketStepService.getTicketStepsByTicketId(ticketId);
        return ResponseEntity.ok(BaseResponse.success("Success", response));
    }

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
        
        AiAnalyzeResponse response = aiService.analyze(request);
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
    @Operation(summary = "Kiểm tra vé trùng lặp theo bán kính không gian (PostGIS ST_DWithin)", description = "Tìm các vé đang mở trong bán kính R mét (mặc định 50m) cùng subCategoryId.")
    public ResponseEntity<BaseResponse<List<DuplicateTicketResponse>>> checkDuplicates(
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam("subCategoryId") UUID subCategoryId) {
        
        LocationCoords coords = parseLocation(location, latitude, longitude);

        List<DuplicateTicketResponse> duplicates = ticketService.findDuplicateTickets(coords.latitude(), coords.longitude(), subCategoryId);
        return ResponseEntity.ok(BaseResponse.success("Kiểm tra vé trùng lặp thành công.", duplicates));
    }

    @PostMapping("/{id}/reroute")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Chuyển tuyến vé (Re-route Ticket)", description = "Chuyển tuyến vé sang phòng ban khác do phòng ban hiện tại không thuộc thẩm quyền.")
    public ResponseEntity<BaseResponse<TicketResponse>> rerouteTicket(
            @PathVariable UUID id,
            @Valid @RequestBody com.cityfix.backend.model.dto.request.RerouteTicketRequest request) {
        TicketResponse response = ticketService.rerouteTicket(id, request);
        return ResponseEntity.ok(BaseResponse.success("Chuyển tuyến vé thành công.", response));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Hủy vé (Resident)", description = "Người dân hủy vé do chính mình tạo (chỉ hủy được khi vé ở trạng thái INITIATED)")
    public ResponseEntity<BaseResponse<TicketResponse>> cancelTicket(
            @PathVariable UUID id) {
        TicketResponse response = ticketService.cancelTicket(id);
        return ResponseEntity.ok(BaseResponse.success("Hủy vé thành công", response));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Lấy lịch sử thao tác của vé", description = "Trả về danh sách các sự kiện (timeline) đã xảy ra với vé, bao gồm lý do luân chuyển hoặc từ chối.")
    public ResponseEntity<BaseResponse<List<com.cityfix.backend.model.dto.response.TicketHistoryResponse>>> getTicketHistory(
            @PathVariable UUID id) {
        List<com.cityfix.backend.model.dto.response.TicketHistoryResponse> response = ticketService.getTicketHistory(id);
        return ResponseEntity.ok(BaseResponse.success("Lấy lịch sử vé thành công", response));
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


