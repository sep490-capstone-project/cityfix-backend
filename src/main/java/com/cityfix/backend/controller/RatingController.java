package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.CreateRatingRequest;
import com.cityfix.backend.model.dto.request.UpdateRatingRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.RatingResponse;
import com.cityfix.backend.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/ratings")
@RequiredArgsConstructor
@Tag(name = "Rating Management", description = "APIs for rating tickets")
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh giá chất lượng xử lý vé", description = "Chỉ người tạo vé hoặc người theo dõi vé mới có quyền đánh giá khi vé đã hoàn tất (RESOLVED/COMPLETED).")
    public ResponseEntity<BaseResponse<RatingResponse>> createRating(
            @PathVariable UUID ticketId,
            @Valid @RequestBody CreateRatingRequest request) {
        RatingResponse response = ratingService.createRating(ticketId, request);
        return ResponseEntity.ok(BaseResponse.success("Đánh giá vé thành công", response));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách đánh giá của vé", description = "Chỉ lấy các đánh giá chưa bị xóa mềm. Ẩn thông tin nếu là đánh giá ẩn danh.")
    public ResponseEntity<BaseResponse<Page<RatingResponse>>> getTicketRatings(
            @PathVariable UUID ticketId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RatingResponse> response = ratingService.getTicketRatings(ticketId, pageable);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách đánh giá thành công", response));
    }

    @GetMapping("/my-rating")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy đánh giá của chính người dùng hiện tại trên vé này", description = "Trả về đánh giá của người dùng hiện tại hoặc null nếu chưa đánh giá.")
    public ResponseEntity<BaseResponse<RatingResponse>> getMyRating(
            @PathVariable UUID ticketId) {
        RatingResponse response = ratingService.getMyRating(ticketId);
        return ResponseEntity.ok(BaseResponse.success("Lấy đánh giá của tôi thành công", response));
    }

    @PutMapping("/{ratingId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chỉnh sửa đánh giá", description = "Chỉ chính chủ mới có quyền cập nhật đánh giá của mình.")
    public ResponseEntity<BaseResponse<RatingResponse>> updateRating(
            @PathVariable UUID ticketId,
            @PathVariable UUID ratingId,
            @Valid @RequestBody UpdateRatingRequest request) {
        RatingResponse response = ratingService.updateRating(ticketId, ratingId, request);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật đánh giá thành công", response));
    }

    @DeleteMapping("/{ratingId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa mềm đánh giá", description = "Chỉ chính chủ hoặc ADMIN mới có quyền xóa đánh giá.")
    public ResponseEntity<BaseResponse<Void>> deleteRating(
            @PathVariable UUID ticketId,
            @PathVariable UUID ratingId) {
        ratingService.deleteRating(ticketId, ratingId);
        return ResponseEntity.ok(BaseResponse.success("Xóa đánh giá thành công", null));
    }
}
