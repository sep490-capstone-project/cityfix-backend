package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.*;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.dto.response.ManagerDepartmentResponse;
import com.cityfix.backend.model.dto.response.SuggestedAssigneeResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.service.ManagerTicketService;
import com.cityfix.backend.service.RatingFrustrationAnalysisService;
import com.cityfix.backend.model.dto.response.RatingFrustrationAnalysisResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.Priority;

import com.cityfix.backend.model.enums.TicketStatus;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manager/tickets")
@RequiredArgsConstructor
@Tag(name = "Manager Ticket Management", description = "APIs for Manual Review, Routing and Ticket Step Management")
public class ManagerTicketController {

    private final ManagerTicketService managerTicketService;
    private final RatingFrustrationAnalysisService ratingFrustrationAnalysisService;

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(
            summary = "Lấy toàn bộ vé có người xử lý thuộc phòng ban của Manager",
            description = "Trả về danh sách phân trang toàn bộ thông tin vé và tất cả các bước xử lý (ticket steps) của vé mà người xử lý thuộc cùng phòng ban với Manager."
    )
    public ResponseEntity<BaseResponse<Page<TicketResponse>>> getManagerAssignedDepartmentTickets(
            @RequestParam(value = "departmentId", required = false) UUID departmentId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TicketResponse> response = managerTicketService.getManagerAssignedDepartmentTickets(departmentId, pageable);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách vé theo phòng ban của quản lý thành công", response));
    }

    @GetMapping("/initiated")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Lấy danh sách vé chờ duyệt", description = "Trả về danh sách vé đang ở trạng thái INITIATED")
    public ResponseEntity<BaseResponse<Page<TicketResponse>>> getInitiatedTickets(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TicketResponse> response = managerTicketService.getInitiatedTickets(pageable);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách vé chờ duyệt thành công", response));
    }

    @GetMapping("/resolved")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(
            summary = "Lấy danh sách vé đang ở trạng thái RESOLVED kèm toàn bộ Ticket Step",
            description = "Trả về danh sách phân trang các vé đang ở trạng thái RESOLVED (có tất cả các bước xử lý đều đã hoàn tất RESOLVED) cùng toàn bộ thông tin vé và các bước xử lý liên kết."
    )
    public ResponseEntity<BaseResponse<Page<TicketResponse>>> getResolvedTickets(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TicketResponse> response = managerTicketService.getResolvedTickets(pageable);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách vé đã giải quyết thành công", response));
    }

    @GetMapping("/department")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Lấy danh sách vé theo phòng ban và bộ lọc", description = "Manager lọc vé theo phòng ban phụ trách (tự detect nếu null), mức độ ưu tiên (priority) và trạng thái (status).")
    public ResponseEntity<BaseResponse<Page<TicketResponse>>> getDepartmentTickets(
            @RequestParam(value = "departmentId", required = false) UUID departmentId,

            @RequestParam(value = "priority", required = false) Priority priority,
            @RequestParam(value = "status", required = false) TicketStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TicketResponse> response = managerTicketService.getDepartmentTickets(departmentId, priority, status, pageable);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách vé phòng ban thành công", response));
    }

    @GetMapping("/departments")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Lấy danh sách phòng ban liên kết với Manager", description = "Trả về danh sách các phòng ban mà Manager phụ trách cùng các cán bộ (role STAFF) thuộc phòng ban đó")
    public ResponseEntity<BaseResponse<List<ManagerDepartmentResponse>>> getManagerDepartments() {
        List<ManagerDepartmentResponse> response = managerTicketService.getManagerDepartments();
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách phòng ban của quản lý thành công", response));
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Duyệt vé và định tuyến", description = "Xác nhận category, đánh dấu SOS và giao việc cho nhiều phòng ban")
    public ResponseEntity<BaseResponse<TicketResponse>> reviewTicket(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewTicketRequest request) {
        TicketResponse response = managerTicketService.reviewTicket(id, request);
        return ResponseEntity.ok(BaseResponse.success("Duyệt và định tuyến vé thành công", response));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Từ chối vé (Manual Review)", description = "Manager từ chối vé, chuyển trạng thái sang REJECTED")
    public ResponseEntity<BaseResponse<TicketResponse>> rejectInitiatedTicket(
            @PathVariable UUID id,
            @Valid @RequestBody RejectTicketRequest request) {
        TicketResponse response = managerTicketService.rejectInitiatedTicket(id, request.getReason());
        return ResponseEntity.ok(BaseResponse.success("Từ chối vé thành công", response));
    }

    @PutMapping("/steps/{stepId}/status")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(
            summary = "Cập nhật trạng thái bước xử lý đang ở RESOLVED thành COMPLETED hoặc IN_PROGRESS",
            description = "Manager xác nhận hoàn thành bước xử lý (COMPLETED) hoặc yêu cầu xử lý lại (IN_PROGRESS). Khi tất cả các bước của vé chuyển sang COMPLETED thì vé tự động chuyển sang COMPLETED."
    )
    public ResponseEntity<BaseResponse<TicketStepResponse>> updateResolvedTicketStepStatus(
            @PathVariable UUID stepId,
            @RequestParam("status") AssignmentStatus status) {
        TicketStepResponse response = managerTicketService.updateResolvedTicketStepStatus(stepId, status);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật trạng thái bước xử lý thành công", response));
    }

    @PutMapping("/steps/{stepId}/deadline")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Cập nhật deadline cho bước xử lý (Ticket Step)", description = "Manager gia hạn hoặc cập nhật deadline cho một bước trong bảng ticket_step")
    public ResponseEntity<BaseResponse<TicketStepResponse>> updateTicketStepDeadline(
            @PathVariable UUID stepId,
            @Valid @RequestBody UpdateTicketStepDeadlineRequest request) {
        TicketStepResponse response = managerTicketService.updateTicketStepDeadline(stepId, request);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật deadline bước xử lý thành công", response));
    }

    @PutMapping("/{ticketId}/steps/deadlines")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Cập nhật deadline cho nhiều bước xử lý của vé", description = "Manager cập nhật deadline cho danh sách các bước trong bảng ticket_step của vé")
    public ResponseEntity<BaseResponse<List<TicketStepResponse>>> updateTicketStepDeadlines(
            @PathVariable UUID ticketId,
            @Valid @RequestBody UpdateTicketStepDeadlinesRequest request) {
        List<TicketStepResponse> response = managerTicketService.updateTicketStepDeadlines(ticketId, request);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật deadline các bước xử lý thành công", response));
    }

    @PutMapping("/steps/{stepId}/dependencies")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Cập nhật danh sách bước phụ thuộc (DAG dependencies) cho bước xử lý",
               description = "Manager cập nhật danh sách các ticket step mà bước này phụ thuộc (phải hoàn tất trước khi bước này có thể bắt đầu)")
    public ResponseEntity<BaseResponse<TicketStepResponse>> updateTicketStepDependencies(
            @PathVariable UUID stepId,
            @Valid @RequestBody UpdateTicketStepDependenciesRequest request) {
        TicketStepResponse response = managerTicketService.updateTicketStepDependencies(stepId, request);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật bước phụ thuộc thành công", response));
    }

    @Deprecated(since = "v2.0", forRemoval = false)
    @PutMapping("/steps/{stepId}/order-index")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Cập nhật thứ tự thực hiện (order_index) cho bước xử lý", description = "Manager cập nhật order_index cho một bước trong bảng ticket_step")
    public ResponseEntity<BaseResponse<TicketStepResponse>> updateTicketStepOrderIndex(
            @PathVariable UUID stepId,
            @Valid @RequestBody UpdateTicketStepOrderRequest request) {
        TicketStepResponse response = managerTicketService.updateTicketStepOrderIndex(stepId, request);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật thứ tự bước xử lý thành công", response));
    }

    @Deprecated(since = "v2.0", forRemoval = false)
    @PutMapping("/{ticketId}/steps/reorder")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Sắp xếp lại thứ tự các bước xử lý của vé", description = "Manager thay đổi toàn bộ thứ tự thực hiện order_index của các bước trong bảng ticket_step")
    public ResponseEntity<BaseResponse<List<TicketStepResponse>>> reorderTicketSteps(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ReorderTicketStepsRequest request) {
        List<TicketStepResponse> response = managerTicketService.reorderTicketSteps(ticketId, request);
        return ResponseEntity.ok(BaseResponse.success("Sắp xếp lại thứ tự các bước thành công", response));
    }

    @GetMapping("/{ticketId}/ratings/frustration-analysis")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Phân tích hệ số bức xúc có trọng số của vé", description = "Chỉ Manager mới có quyền xem chi tiết điểm phạt bức xúc, số lượng người theo dõi, ngưỡng kích hoạt và trạng thái cảnh báo khủng hoảng.")
    public ResponseEntity<BaseResponse<RatingFrustrationAnalysisResponse>> getFrustrationAnalysis(
            @PathVariable UUID ticketId) {
        RatingFrustrationAnalysisResponse response = ratingFrustrationAnalysisService.analyzeFrustration(ticketId);
        return ResponseEntity.ok(BaseResponse.success("Phân tích hệ số bức xúc thành công", response));
    }
}
