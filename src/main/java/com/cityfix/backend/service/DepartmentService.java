package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.CreateDepartmentRequest;
import com.cityfix.backend.model.dto.request.UpdateDepartmentRequest;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DepartmentService {
    DepartmentResponse createDepartment(CreateDepartmentRequest request);
    DepartmentResponse updateDepartment(UUID id, UpdateDepartmentRequest request);
    DepartmentResponse getDepartmentById(UUID id);
    Page<DepartmentResponse> getAllDepartments(Pageable pageable, Boolean activeOnly);
    void deleteDepartment(UUID id);
}
