package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.service.StaffTicketService;
import com.cityfix.backend.service.TicketStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ticket-steps")
@RequiredArgsConstructor
@Tag(name = "Ticket Step Management", description = "APIs for managing individual ticket steps")
public class TicketStepController {

    private final TicketStepService ticketStepService;
    private final StaffTicketService staffTicketService;

    @PatchMapping("/{stepId}/status")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Cập nhật trạng thái bước xử lý (Ticket Step)", description = "Chỉ cho phép vai trò STAFF được phân công trực tiếp (assignee) cập nhật trạng thái bước xử lý của chính mình.")
    public ResponseEntity<BaseResponse<TicketStepResponse>> updateTicketStepStatus(
            @PathVariable UUID stepId,
            @RequestParam("status") AssignmentStatus status) {
        TicketStepResponse response = ticketStepService.updateTicketStepStatus(stepId, status);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật trạng thái bước xử lý thành công", response));
    }

    @GetMapping("/ticket/{ticketId}")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER')")
    @Operation(summary = "Lấy danh sách các bước xử lý theo ticketId", description = "Chỉ cho phép vai trò STAFF và MANAGER. Chặn ADMIN và các vai trò khác.")
    public ResponseEntity<BaseResponse<List<TicketStepResponse>>> getTicketStepsByTicketId(@PathVariable UUID ticketId) {
        List<TicketStepResponse> response = ticketStepService.getTicketStepsByTicketId(ticketId);
        return ResponseEntity.ok(BaseResponse.success("Success", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Xóa mềm bước xử lý (Soft delete)", description = "Chỉ cho phép vai trò MANAGER. Chặn STAFF, ADMIN và các vai trò khác.")
    public ResponseEntity<BaseResponse<Void>> deleteTicketStep(@PathVariable UUID id) {
        ticketStepService.deleteTicketStep(id);
        return ResponseEntity.ok(BaseResponse.success("Xóa bước xử lý thành công", null));
    }

    @PostMapping(value = "/{stepId}/evidences", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "Đính kèm bằng chứng xử lý cho bước", description = "Upload nhiều files bằng chứng và kèm ghi chú cho một bước cụ thể. Bước sẽ tự động chuyển sang RESOLVED khi upload thành công.")
    public ResponseEntity<BaseResponse<List<com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse>>> submitEvidence(
            @PathVariable UUID stepId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "resolutionNote", required = false) String resolutionNote) {
        List<com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse> response = ticketStepService.submitEvidence(stepId, files, resolutionNote);
        return ResponseEntity.ok(BaseResponse.success("Nộp bằng chứng xử lý cho bước thành công", response));
    }
}
