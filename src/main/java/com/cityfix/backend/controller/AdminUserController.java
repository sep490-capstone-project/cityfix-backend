package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.AdminUserRequest;
import com.cityfix.backend.model.dto.response.AdminUserResponse;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin User Management", description = "APIs quản lý người dùng dành cho Admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy danh sách người dùng", description = "Lấy toàn bộ danh sách người dùng (không bao gồm đã bị xóa)")
    public ResponseEntity<BaseResponse<List<AdminUserResponse>>> getAllUsers() {
        List<AdminUserResponse> response = adminUserService.getAllUsers();
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách người dùng thành công", response));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo mới người dùng", description = "Thêm người dùng mới, gán Role")
    public ResponseEntity<BaseResponse<AdminUserResponse>> createUser(
            @Valid @RequestBody AdminUserRequest request) {
        AdminUserResponse response = adminUserService.createUser(request);
        return ResponseEntity.ok(BaseResponse.success("Tạo người dùng thành công", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật người dùng", description = "Chỉnh sửa thông tin, Role, trạng thái tài khoản. Cờ isDispatcher chỉ cập nhật được cho role STAFF")
    public ResponseEntity<BaseResponse<AdminUserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUserRequest request) {
        AdminUserResponse response = adminUserService.updateUser(id, request);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật người dùng thành công", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa người dùng", description = "Xóa mềm người dùng (cập nhật deletedAt)")
    public ResponseEntity<BaseResponse<Void>> deleteUser(@PathVariable UUID id) {
        adminUserService.deleteUser(id);
        return ResponseEntity.ok(BaseResponse.success("Xóa người dùng thành công", null));
    }
}
