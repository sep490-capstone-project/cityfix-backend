package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRoutingRuleRequest(
    @NotNull(message = "Phòng ban không được để trống")
    UUID departmentId,

    @NotNull(message = "Loại sự cố không được để trống")
    UUID categoryId,

    @NotBlank(message = "Tỉnh/Thành phố không được để trống")
    String province,

    @NotBlank(message = "Phường/Xã không được để trống")
    String ward
) {}
