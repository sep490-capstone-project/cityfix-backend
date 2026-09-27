package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/staff/tickets")
@RequiredArgsConstructor
public class StaffTicketController {

    private final TicketService ticketService;

    @GetMapping("/assigned")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Lấy danh sách vé được phân công cho phòng ban của cán bộ", description = "Trích xuất phòng ban từ thông tin xác thực cán bộ đang đăng nhập, hỗ trợ phân trang và lọc theo trạng thái phân công.")
    public ResponseEntity<BaseResponse<Page<TicketResponse>>> getAssignedTicketsForStaff(
            @RequestParam(value = "status", required = false) AssignmentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TicketResponse> response = ticketService.getAssignedTicketsForStaff(status, pageable);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách vé phân công thành công.", response));
    }
}
