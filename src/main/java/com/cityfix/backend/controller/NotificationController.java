package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.NotificationResponse;
import com.cityfix.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification Management", description = "APIs for user and manager notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lấy danh sách thông báo của người dùng hiện tại",
            description = "Hỗ trợ lấy danh sách thông báo phân trang của người dùng hoặc quản lý đang đăng nhập, có thể lọc theo trạng thái đã đọc (isRead)."
    )
    public ResponseEntity<BaseResponse<Page<NotificationResponse>>> getMyNotifications(
            @RequestParam(value = "isRead", required = false) Boolean isRead,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<NotificationResponse> response = notificationService.getMyNotifications(isRead, pageable);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách thông báo thành công", response));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Đếm số lượng thông báo chưa đọc",
            description = "Trả về tổng số thông báo chưa đọc của người dùng hiện tại."
    )
    public ResponseEntity<BaseResponse<Long>> getUnreadCount() {
        long count = notificationService.getUnreadCount();
        return ResponseEntity.ok(BaseResponse.success("Lấy số lượng thông báo chưa đọc thành công", count));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Đánh dấu thông báo đã đọc",
            description = "Đánh dấu một thông báo cụ thể của người dùng hiện tại là đã đọc."
    )
    public ResponseEntity<BaseResponse<NotificationResponse>> markAsRead(@PathVariable UUID id) {
        NotificationResponse response = notificationService.markAsRead(id);
        return ResponseEntity.ok(BaseResponse.success("Đánh dấu đã đọc thành công", response));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Đánh dấu tất cả thông báo đã đọc",
            description = "Đánh dấu toàn bộ thông báo chưa đọc của người dùng hiện tại thành đã đọc."
    )
    public ResponseEntity<BaseResponse<Void>> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(BaseResponse.success("Đánh dấu tất cả thông báo đã đọc thành công", null));
    }
}
