package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.service.StaffTicketService;
import com.cityfix.backend.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/staff/tickets")
@RequiredArgsConstructor
public class StaffTicketController {

    private final TicketService ticketService;
    private final StaffTicketService staffTicketService;

    @GetMapping("/assigned")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Lấy danh sách vé được phân công cho phòng ban của cán bộ", description = "Trích xuất phòng ban từ thông tin xác thực cán bộ đang đăng nhập, hỗ trợ phân trang và lọc theo trạng thái phân công.")
    public ResponseEntity<BaseResponse<Page<TicketResponse>>> getAssignedTicketsForStaff(
            @RequestParam(value = "status", required = false) AssignmentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TicketResponse> response = ticketService.getAssignedTicketsForStaff(status, pageable);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách vé phân công thành công.", response));
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "Tiếp nhận & Phân công vé", description = "Cán bộ xác nhận vé đúng chuyên môn và phân công cho nhân viên cụ thể.")
    public ResponseEntity<BaseResponse<TicketResponse>> assignTicket(
            @PathVariable java.util.UUID id,
            @jakarta.validation.Valid @RequestBody com.cityfix.backend.model.dto.request.AssignTicketRequest request) {
        TicketResponse response = ticketService.assignTicket(id, request);
        return ResponseEntity.ok(BaseResponse.success("Phân công vé thành công", response));
    }

    @PutMapping("/{id}/forward")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "Chuyển giao vé (Reroute)", description = "Chuyển vé sang phòng ban khác nếu không thuộc thẩm quyền")
    public ResponseEntity<BaseResponse<TicketResponse>> forwardTicket(
            @PathVariable java.util.UUID id,
            @jakarta.validation.Valid @RequestBody com.cityfix.backend.model.dto.request.RejectTicketRequest request) {
        TicketResponse response = ticketService.forwardTicket(id, request);
        return ResponseEntity.ok(BaseResponse.success("Chuyển giao vé thành công", response));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "Từ chối hồ sơ", description = "Điều phối viên từ chối hẳn vé (chuyển thành REJECTED) kèm lý do")
    public ResponseEntity<BaseResponse<TicketResponse>> rejectAssignedTicket(
            @PathVariable java.util.UUID id,
            @jakarta.validation.Valid @RequestBody com.cityfix.backend.model.dto.request.RejectTicketRequest request) {
        TicketResponse response = ticketService.rejectAssignedTicket(id, request.getReason());
        return ResponseEntity.ok(BaseResponse.success("Từ chối hồ sơ thành công", response));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "Cập nhật trạng thái vé", description = "Cán bộ thay đổi trạng thái vé. Hỗ trợ các trạng thái INITIATED, OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, COMPLETED.")
    public ResponseEntity<BaseResponse<TicketResponse>> updateTicketStatus(
            @PathVariable java.util.UUID id,
            @RequestParam com.cityfix.backend.model.enums.TicketStatus targetStatus,
            @RequestBody(required = false) @jakarta.validation.Valid com.cityfix.backend.model.dto.request.UpdateStaffTicketStatusRequest request) {
        TicketResponse response = staffTicketService.updateTicketStatus(id, targetStatus, request);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật trạng thái vé thành công", response));
    }

    @PostMapping(value = "/{id}/evidences", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "Đính kèm bằng chứng xử lý", description = "Upload nhiều files (hình ảnh, video) và kèm ghi chú. Hỗ trợ truyền stepId hoặc id của vé/bước.")
    public ResponseEntity<BaseResponse<java.util.List<com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse>>> submitEvidence(
            @PathVariable java.util.UUID id,
            @RequestPart("files") java.util.List<org.springframework.web.multipart.MultipartFile> files,
            @RequestParam(value = "stepId", required = false) java.util.UUID stepId,
            @RequestParam(value = "resolutionNote", required = false) String resolutionNote) {
        java.util.UUID targetId = (stepId != null) ? stepId : id;
        java.util.List<com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse> response = staffTicketService.submitEvidence(targetId, files, resolutionNote);
        return ResponseEntity.ok(BaseResponse.success("Nộp bằng chứng xử lý thành công", response));
    }

    public ResponseEntity<BaseResponse<java.util.List<com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse>>> submitEvidence(
            java.util.UUID id,
            java.util.List<org.springframework.web.multipart.MultipartFile> files,
            String resolutionNote) {
        return submitEvidence(id, files, null, resolutionNote);
    }
}
