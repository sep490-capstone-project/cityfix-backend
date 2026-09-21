package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.UpdateProfileRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.UserProfileResponse;
import com.cityfix.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Quản lý Profile Người dùng")
public class ProfileController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy thông tin profile của người dùng hiện tại", description = "Trả về thông tin chi tiết profile của tài khoản đã xác thực thông qua JWT token.")
    public ResponseEntity<BaseResponse<UserProfileResponse>> getMyProfile() {
        UserProfileResponse response = userService.getMyProfile();
        return ResponseEntity.ok(BaseResponse.success("Lấy thông tin profile thành công.", response));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật thông tin profile của người dùng hiện tại", description = "Cập nhật các trường thông tin cá nhân của người dùng đã xác thực và trả về thông tin mới.")
    public ResponseEntity<BaseResponse<UserProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        UserProfileResponse response = userService.updateMyProfile(request);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật profile thành công.", response));
    }
}
