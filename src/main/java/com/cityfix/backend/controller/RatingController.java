package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.CreateRatingRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.RatingResponse;
import com.cityfix.backend.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
}
