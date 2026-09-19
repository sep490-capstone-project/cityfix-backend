package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.CreateRoutingRuleRequest;
import com.cityfix.backend.model.dto.request.UpdateRoutingRuleRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.RoutingRuleResponse;
import com.cityfix.backend.service.RoutingRuleService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/routing-rules")
@RequiredArgsConstructor
@Tag(name = "Routing Rule Management", description = "APIs quản lý quy tắc định tuyến xử lý sự cố (Task 20)")
public class RoutingRuleController {

    private final RoutingRuleService routingRuleService;

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Tạo mới quy tắc định tuyến", description = "Phân công phòng ban quản lý loại sự cố tại khu vực cụ thể")
    public ResponseEntity<BaseResponse<RoutingRuleResponse>> createRoutingRule(
            @Valid @RequestBody CreateRoutingRuleRequest request) {
        RoutingRuleResponse response = routingRuleService.createRoutingRule(request);
        return ResponseEntity.ok(BaseResponse.success("Tạo quy tắc định tuyến thành công", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    @Operation(summary = "Tìm kiếm / Tìm danh sách quy tắc định tuyến", description = "Hỗ trợ lọc theo categoryId, province, ward, departmentId và phân trang")
    public ResponseEntity<BaseResponse<Page<RoutingRuleResponse>>> searchRoutingRules(
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "province", required = false) String province,
            @RequestParam(value = "ward", required = false) String ward,
            @RequestParam(value = "departmentId", required = false) UUID departmentId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<RoutingRuleResponse> response = routingRuleService.searchRoutingRules(categoryId, province, ward, departmentId, pageable);
        return ResponseEntity.ok(BaseResponse.success("Tìm kiếm quy tắc định tuyến thành công", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    @Operation(summary = "Chi tiết quy tắc định tuyến", description = "Lấy chi tiết quy tắc định tuyến theo ID")
    public ResponseEntity<BaseResponse<RoutingRuleResponse>> getRoutingRuleById(@PathVariable UUID id) {
        RoutingRuleResponse response = routingRuleService.getRoutingRuleById(id);
        return ResponseEntity.ok(BaseResponse.success("Lấy thông tin quy tắc định tuyến thành công", response));
    }

    @GetMapping("/department/{departmentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    @Operation(summary = "Danh sách quy tắc định tuyến theo cơ quan phụ trách", description = "Lấy danh sách quy tắc định tuyến gán cho cơ quan cụ thể")
    public ResponseEntity<BaseResponse<List<RoutingRuleResponse>>> getRoutingRulesByDepartment(
            @PathVariable UUID departmentId) {
        List<RoutingRuleResponse> response = routingRuleService.getRoutingRulesByDepartment(departmentId);
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách quy tắc định tuyến theo phòng ban thành công", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Cập nhật quy tắc định tuyến", description = "Cập nhật phòng ban, loại sự cố hoặc khu vực của quy tắc")
    public ResponseEntity<BaseResponse<RoutingRuleResponse>> updateRoutingRule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoutingRuleRequest request) {
        RoutingRuleResponse response = routingRuleService.updateRoutingRule(id, request);
        return ResponseEntity.ok(BaseResponse.success("Cập nhật quy tắc định tuyến thành công", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Xóa quy tắc định tuyến", description = "Xóa hẳn quy tắc định tuyến khỏi hệ thống")
    public ResponseEntity<BaseResponse<Void>> deleteRoutingRule(@PathVariable UUID id) {
        routingRuleService.deleteRoutingRule(id);
        return ResponseEntity.ok(BaseResponse.success("Xóa quy tắc định tuyến thành công", null));
    }
}
