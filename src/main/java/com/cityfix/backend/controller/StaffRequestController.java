package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.CreateStaffRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.StaffRequestResponse;
import com.cityfix.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/staff/requests")
@RequiredArgsConstructor
@Tag(name = "Staff Request Management", description = "APIs for staff to create requests to department managers")
public class StaffRequestController {

    private final NotificationService notificationService;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(
            summary = "Staff tạo request gửi đến Manager quản lý",
            description = "Nhân viên tạo yêu cầu gửi tới người quản lý (Manager) phụ trách phòng ban của mình. Yêu cầu này trở thành thông báo hiển thị trong danh sách thông báo của Manager và đẩy qua WebSocket real-time."
    )
    public ResponseEntity<BaseResponse<StaffRequestResponse>> createStaffRequest(
            @Valid @RequestBody CreateStaffRequest request) {
        StaffRequestResponse response = notificationService.sendStaffRequestToManager(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("Gửi yêu cầu đến quản lý thành công", response));
    }
}
