package com.cityfix.backend.controller;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.request.AdminTicketFilterRequest;
import com.cityfix.backend.model.dto.response.AdminTicketDetailResponse;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.service.AdminTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Ticket Management", description = "APIs giám sát, tìm kiếm và tra cứu hồ sơ sự cố toàn hệ thống dành cho System Admin")
public class AdminTicketController {

    private final AdminTicketService adminTicketService;

    @GetMapping
    @Operation(
            summary = "Xem, tìm kiếm và lọc danh sách toàn bộ hồ sơ sự cố trên hệ thống",
            description = "Dành cho System Admin giám sát tổng thể tình hình tiếp nhận sự cố (bao gồm cả hồ sơ chưa phân bổ) mà không bị giới hạn bởi cơ quan phụ trách."
    )
    public ResponseEntity<BaseResponse<Page<TicketResponse>>> getAllTickets(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) TicketStatus status,
            @RequestParam(value = "priority", required = false) Priority priority,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "subCategoryId", required = false) UUID subCategoryId,
            @RequestParam(value = "departmentId", required = false) UUID departmentId,
            @RequestParam(value = "unassigned", required = false) Boolean unassigned,
            @RequestParam(value = "province", required = false) String province,
            @RequestParam(value = "ward", required = false) String ward,
            @RequestParam(value = "isLifeThreatening", required = false) Boolean isLifeThreatening,
            @RequestParam(value = "needsManualReview", required = false) Boolean needsManualReview,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        LocalDateTime parsedFrom = parseDateTime(fromDate, false);
        LocalDateTime parsedTo = parseDateTime(toDate, true);

        if (parsedFrom != null && parsedTo != null && parsedFrom.isAfter(parsedTo)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Ngày bắt đầu không được lớn hơn ngày kết thúc");
        }

        AdminTicketFilterRequest filter = AdminTicketFilterRequest.builder()
                .keyword(keyword)
                .status(status)
                .priority(priority)
                .categoryId(categoryId)
                .subCategoryId(subCategoryId)
                .departmentId(departmentId)
                .unassigned(unassigned)
                .province(province)
                .ward(ward)
                .isLifeThreatening(isLifeThreatening)
                .needsManualReview(needsManualReview)
                .fromDate(parsedFrom)
                .toDate(parsedTo)
                .build();

        Page<TicketResponse> response = adminTicketService.getAllTickets(filter, pageable);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách hồ sơ sự cố thành công", response));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Xem toàn bộ thông tin chi tiết của một hồ sơ sự cố bất kỳ (Read-only)",
            description = "Trả về đầy đủ dữ liệu vị trí, hình ảnh, người gửi (kể cả ẩn danh), người xử lý, các bước xử lý kèm bằng chứng, lịch sử thao tác và đánh giá."
    )
    public ResponseEntity<BaseResponse<AdminTicketDetailResponse>> getTicketDetail(@PathVariable UUID id) {
        AdminTicketDetailResponse response = adminTicketService.getTicketDetail(id);
        return ResponseEntity.ok(BaseResponse.success("Lấy chi tiết hồ sơ sự cố thành công", response));
    }

    private LocalDateTime parseDateTime(String raw, boolean endOfDayIfDateOnly) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            LocalDate date = LocalDate.parse(trimmed);
            return endOfDayIfDateOnly ? date.atTime(23, 59, 59) : date.atStartOfDay();
        } catch (Exception ignored) {
        }
        throw new AppException(ErrorCode.INVALID_REQUEST, "Định dạng ngày giờ không hợp lệ: " + raw);
    }
}
