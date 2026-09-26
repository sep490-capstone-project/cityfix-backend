package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.ReviewTicketRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tickets")
@RequiredArgsConstructor
@Tag(name = "Admin Ticket Management", description = "APIs for Manual Review and Routing (Task 11)")
public class AdminTicketController {

    private final TicketService ticketService;

    @GetMapping("/pending-review")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    @Operation(summary = "Lấy danh sách vé chờ duyệt", description = "Trả về danh sách vé đang ở trạng thái PENDING_REVIEW")
    public ResponseEntity<BaseResponse<Page<TicketResponse>>> getPendingReviewTickets(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TicketResponse> response = ticketService.getPendingReviewTickets(pageable);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách vé chờ duyệt thành công", response));
    }

    @GetMapping("/{id}/suggested-departments")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    @Operation(summary = "Gợi ý phòng ban cho vé", description = "Tìm phòng ban phù hợp dựa trên routing rule (Category và Địa chỉ)")
    public ResponseEntity<BaseResponse<List<DepartmentResponse>>> getSuggestedDepartments(
            @PathVariable UUID id,
            @RequestParam(value = "categoryId", required = false) UUID categoryId) {
        List<DepartmentResponse> response = ticketService.getSuggestedDepartments(id, categoryId);
        return ResponseEntity.ok(BaseResponse.success("Lấy gợi ý phòng ban thành công", response));
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    @Operation(summary = "Duyệt vé và định tuyến", description = "Xác nhận category, đánh dấu SOS và giao việc cho nhiều phòng ban")
    public ResponseEntity<BaseResponse<TicketResponse>> reviewTicket(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewTicketRequest request) {
        TicketResponse response = ticketService.reviewTicket(id, request);
        return ResponseEntity.ok(BaseResponse.success("Duyệt và định tuyến vé thành công", response));
    }
}
