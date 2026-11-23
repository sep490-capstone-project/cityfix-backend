package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateRoutingRuleRequest(
    @NotNull(message = "Phòng ban không được để trống")
    UUID departmentId,

    @NotNull(message = "Loại sự cố không được để trống")
    UUID categoryId,

    @NotBlank(message = "Tỉnh/Thành phố không được để trống")
    @Size(max = 100, message = "Tỉnh/Thành phố không được vượt quá 100 ký tự")
    String province,

    @NotBlank(message = "Phường/Xã không được để trống")
    @Size(max = 100, message = "Phường/Xã không được vượt quá 100 ký tự")
    String ward
) {}
