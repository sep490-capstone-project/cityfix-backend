package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.CreateDepartmentRequest;
import com.cityfix.backend.model.dto.request.UpdateDepartmentRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.service.DepartmentService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "Department Management", description = "APIs quản lý cơ quan phụ trách (Task 18)")
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo mới cơ quan phụ trách", description = "Dành cho Admin tạo cơ quan xử lý sự cố")
    public ResponseEntity<BaseResponse<DepartmentResponse>> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse response = departmentService.createDepartment(request);
        return ResponseEntity.ok(BaseResponse.success("Tạo cơ quan phụ trách thành công", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Lấy danh sách cơ quan phụ trách", description = "Hỗ trợ phân trang và lọc các phòng ban đang hoạt động")
    public ResponseEntity<BaseResponse<Page<DepartmentResponse>>> getAllDepartments(
            @RequestParam(value = "activeOnly", required = false, defaultValue = "false") Boolean activeOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<DepartmentResponse> response = departmentService.getAllDepartments(pageable, activeOnly);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách cơ quan phụ trách thành công", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Chi tiết cơ quan phụ trách", description = "Lấy thông tin chi tiết cơ quan theo ID")
    public ResponseEntity<BaseResponse<DepartmentResponse>> getDepartmentById(@PathVariable UUID id) {
        DepartmentResponse response = departmentService.getDepartmentById(id);
        return ResponseEntity.ok(BaseResponse.success("Lấy thông tin cơ quan phụ trách thành công", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật thông tin cơ quan phụ trách", description = "Cập nhật tên, cấp quản lý, email liên hệ, số điện thoại, trạng thái hoạt động")
    public ResponseEntity<BaseResponse<DepartmentResponse>> updateDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        DepartmentResponse response = departmentService.updateDepartment(id, request);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật cơ quan phụ trách thành công", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Vô hiệu hóa cơ quan phụ trách", description = "Chuyển trạng thái isActive của cơ quan sang false")
    public ResponseEntity<BaseResponse<Void>> deleteDepartment(@PathVariable UUID id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.ok(BaseResponse.success("Vô hiệu hóa cơ quan phụ trách thành công", null));
    }
}
