package com.cityfix.backend.controller;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.response.AdminAnalyticsResponse;
import com.cityfix.backend.model.dto.response.AdminDashboardSummaryResponse;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Dashboard & Analytics", description = "APIs tổng hợp chỉ số KPI và phân tích thống kê/xuất báo cáo dành cho System Admin")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard/summary")
    @Operation(
            summary = "Xem bảng tổng hợp các chỉ số KPI quan trọng nhất của hệ thống",
            description = "Cung cấp cái nhìn tổng quan về sức khỏe hệ thống (hồ sơ sự cố, quá hạn, SOS), số lượng tài khoản theo vai trò, cấu hình hệ thống và tỷ lệ giải quyết hồ sơ của từng cơ quan."
    )
    public ResponseEntity<BaseResponse<AdminDashboardSummaryResponse>> getDashboardSummary() {
        AdminDashboardSummaryResponse response = adminDashboardService.getDashboardSummary();
        return ResponseEntity.ok(BaseResponse.success("Lấy thông tin tổng hợp KPI hệ thống thành công", response));
    }

    @GetMapping("/analytics")
    @Operation(
            summary = "Trích xuất dữ liệu thống kê chi tiết theo thời gian, khu vực địa lý và cơ quan (hỗ trợ xuất CSV)",
            description = "Phân tích xu hướng sự cố theo thời gian (DAY/WEEK/MONTH), khu vực địa lý (province/ward), hiệu quả hoạt động của các cơ quan và danh mục. Truyền format=csv để tải trực tiếp file báo cáo CSV."
    )
    public ResponseEntity<?> getAnalytics(
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate,
            @RequestParam(value = "groupBy", required = false, defaultValue = "DAY") String groupBy,
            @RequestParam(value = "province", required = false) String province,
            @RequestParam(value = "ward", required = false) String ward,
            @RequestParam(value = "departmentId", required = false) UUID departmentId,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "format", required = false, defaultValue = "json") String format) {

        LocalDateTime parsedFrom = parseDateTime(fromDate, false);
        LocalDateTime parsedTo = parseDateTime(toDate, true);

        if (parsedFrom != null && parsedTo != null && parsedFrom.isAfter(parsedTo)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Ngày bắt đầu không được lớn hơn ngày kết thúc");
        }

        if ("csv".equalsIgnoreCase(format)) {
            byte[] csvBytes = adminDashboardService.exportAnalyticsCsv(
                    parsedFrom, parsedTo, groupBy, province, ward, departmentId, categoryId
            );
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cityfix_analytics_report.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csvBytes);
        }

        AdminAnalyticsResponse response = adminDashboardService.getAnalytics(
                parsedFrom, parsedTo, groupBy, province, ward, departmentId, categoryId
        );
        return ResponseEntity.ok(BaseResponse.success("Trích xuất dữ liệu thống kê thành công", response));
    }

    @GetMapping("/analytics/export")
    @Operation(
            summary = "Xuất báo cáo thống kê ra file CSV (mở trực tiếp bằng Excel)",
            description = "Trích xuất dữ liệu thống kê theo bộ lọc và trả về file CSV chuẩn UTF-8 (kèm BOM) để mở trực tiếp trên Microsoft Excel."
    )
    public ResponseEntity<byte[]> exportAnalyticsCsv(
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate,
            @RequestParam(value = "groupBy", required = false, defaultValue = "DAY") String groupBy,
            @RequestParam(value = "province", required = false) String province,
            @RequestParam(value = "ward", required = false) String ward,
            @RequestParam(value = "departmentId", required = false) UUID departmentId,
            @RequestParam(value = "categoryId", required = false) UUID categoryId) {

        LocalDateTime parsedFrom = parseDateTime(fromDate, false);
        LocalDateTime parsedTo = parseDateTime(toDate, true);

        if (parsedFrom != null && parsedTo != null && parsedFrom.isAfter(parsedTo)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Ngày bắt đầu không được lớn hơn ngày kết thúc");
        }

        byte[] csvBytes = adminDashboardService.exportAnalyticsCsv(
                parsedFrom, parsedTo, groupBy, province, ward, departmentId, categoryId
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cityfix_analytics_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
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
            return endOfDayIfDateOnly ? date.atTime(LocalTime.MAX) : date.atStartOfDay();
        } catch (Exception ignored) {
        }
        throw new AppException(ErrorCode.INVALID_REQUEST, "Định dạng ngày giờ không hợp lệ: " + raw);
    }
}
