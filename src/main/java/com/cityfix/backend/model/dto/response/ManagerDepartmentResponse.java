package com.cityfix.backend.model.dto.response;

import com.cityfix.backend.model.enums.DepartmentLevel;
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
public class ManagerDepartmentResponse {

    private UUID departmentId;
    private String departmentName;
    private DepartmentLevel level;
    private String contactEmail;
    private String phoneNumber;
    private List<StaffSummaryResponse> staffs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffSummaryResponse {
        private UUID userId;
        private String username;
        private String fullName;
        private String email;
        private String phoneNumber;
    }
}
