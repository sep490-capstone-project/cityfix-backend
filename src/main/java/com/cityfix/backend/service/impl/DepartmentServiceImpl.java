package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.DepartmentMapper;
import com.cityfix.backend.model.dto.request.CreateDepartmentRequest;
import com.cityfix.backend.model.dto.request.UpdateDepartmentRequest;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        if (departmentRepository.existsByNameAndIsDeletedFalse(request.name())) {
            throw new AppException(ErrorCode.DEPARTMENT_NAME_EXISTS);
        }

        Department department = departmentMapper.toEntity(request);
        Department savedDepartment = departmentRepository.save(department);

        return departmentMapper.toResponse(savedDepartment);
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartment(UUID id, UpdateDepartmentRequest request) {
        Department department = departmentRepository.findByDepartmentIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));

        if (request.name() != null && !request.name().equals(department.getName())) {
            if (departmentRepository.existsByNameAndDepartmentIdNotAndIsDeletedFalse(request.name(), id)) {
                throw new AppException(ErrorCode.DEPARTMENT_NAME_EXISTS);
            }
        }

        departmentMapper.updateEntityFromRequest(request, department);

        if (request.isActive() != null) {
            if (Boolean.FALSE.equals(request.isActive())) {
                department.setDeleted(true);
                department.setDeletedAt(LocalDateTime.now());
            } else {
                department.setDeleted(false);
                department.setDeletedAt(null);
            }
        }

        Department updatedDepartment = departmentRepository.save(department);

        return departmentMapper.toResponse(updatedDepartment);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(UUID id) {
        Department department = departmentRepository.findByDepartmentIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));

        return departmentMapper.toResponse(department);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DepartmentResponse> getAllDepartments(Pageable pageable, Boolean activeOnly) {
        Page<Department> page;
        if (Boolean.TRUE.equals(activeOnly)) {
            page = departmentRepository.findByIsDeletedFalse(pageable);
        } else {
            page = departmentRepository.findAll(pageable);
        }
        return page.map(departmentMapper::toResponse);
    }

    @Override
    @Transactional
    public void deleteDepartment(UUID id) {
        Department department = departmentRepository.findByDepartmentIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));

        if (department.getOfficers() != null && !department.getOfficers().isEmpty()) {
            department.getOfficers().forEach(user -> user.setDepartment(null));
        }

        department.setDeleted(true);
        department.setDeletedAt(LocalDateTime.now());
        departmentRepository.save(department);
    }
}
