package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignDepartmentUsersRequest {

    @NotEmpty(message = "Danh sách userIds không được để trống")
    private List<@NotNull(message = "Mã nhân sự (userId) không được null") UUID> userIds;
}
