package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.DepartmentMapper;
import com.cityfix.backend.model.dto.request.AssignDepartmentUsersRequest;
import com.cityfix.backend.model.dto.request.CreateDepartmentRequest;
import com.cityfix.backend.model.dto.request.UpdateDepartmentRequest;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.dto.response.DepartmentUserResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.UserDepartment;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.repository.UserDepartmentRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        if (departmentRepository.existsByNameAndIsDeletedFalse(request.name())) {
            throw new AppException(ErrorCode.DEPARTMENT_NAME_EXISTS);
        }

        Department department = departmentMapper.toEntity(request);
        if (request.managerId() != null) {
            User manager = userRepository.findById(request.managerId())
                    .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
            department.setManager(manager);
        }
        Department savedDepartment = departmentRepository.save(department);
        if (savedDepartment.getManager() != null) {
            syncManagerDepartmentMapping(savedDepartment.getManager(), savedDepartment);
        }

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

        if (request.managerId() != null) {
            User manager = userRepository.findById(request.managerId())
                    .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
            department.setManager(manager);
            syncManagerDepartmentMapping(manager, department);
        } else {
            department.setManager(null);
        }

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

    @Override
    @Transactional
    public List<DepartmentUserResponse> assignUsersToDepartment(UUID departmentId, AssignDepartmentUsersRequest request) {
        Department department = departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));

        if (request.getUserIds() != null) {
            List<UUID> distinctUserIds = request.getUserIds().stream().distinct().toList();
            for (UUID userId : distinctUserIds) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

                if (user.isDeleted()) {
                    throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND);
                }

                String roleName = user.getRole() != null ? user.getRole().getRoleName().toUpperCase() : "";
                if (!"STAFF".equals(roleName) && !"MANAGER".equals(roleName)) {
                    throw new AppException(ErrorCode.INVALID_REQUEST);
                }

                if (!userDepartmentRepository.existsByUserUserIdAndDepartmentDepartmentId(userId, departmentId)) {
                    UserDepartment mapping = UserDepartment.builder()
                            .user(user)
                            .department(department)
                            .createdAt(LocalDateTime.now())
                            .build();
                    userDepartmentRepository.save(mapping);
                }

                if (user.getDepartment() == null) {
                    user.setDepartment(department);
                    userRepository.save(user);
                }
            }
        }

        return getDepartmentUsers(departmentId);
    }

    @Override
    @Transactional
    public void removeUserFromDepartment(UUID departmentId, UUID userId) {
        Department department = departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        userDepartmentRepository.deleteByUserUserIdAndDepartmentDepartmentId(userId, departmentId);

        if (user.getDepartment() != null && user.getDepartment().getDepartmentId().equals(departmentId)) {
            List<Department> remaining = userDepartmentRepository.findDepartmentsByUserId(userId);
            Department nextDept = remaining.stream()
                    .filter(d -> !d.getDepartmentId().equals(departmentId))
                    .findFirst()
                    .orElse(null);
            user.setDepartment(nextDept);
            userRepository.save(user);
        }

        if (department.getManager() != null && department.getManager().getUserId().equals(userId)) {
            department.setManager(null);
            departmentRepository.save(department);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentUserResponse> getDepartmentUsers(UUID departmentId) {
        departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));

        List<UserDepartment> mappings = userDepartmentRepository.findDepartmentUsersWithRole(departmentId);
        return mappings.stream()
                .map(ud -> {
                    User u = ud.getUser();
                    return DepartmentUserResponse.builder()
                            .userId(u.getUserId())
                            .email(u.getEmail())
                            .fullName(u.getFullName())
                            .phoneNumber(u.getPhoneNumber())
                            .roleName(u.getRole() != null ? u.getRole().getRoleName() : null)
                            .status(u.getStatus())
                            .joinedAt(ud.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private void syncManagerDepartmentMapping(User manager, Department department) {
        if (!userDepartmentRepository.existsByUserUserIdAndDepartmentDepartmentId(manager.getUserId(), department.getDepartmentId())) {
            UserDepartment mapping = UserDepartment.builder()
                    .user(manager)
                    .department(department)
                    .createdAt(LocalDateTime.now())
                    .build();
            userDepartmentRepository.save(mapping);
        }
        if (manager.getDepartment() == null) {
            manager.setDepartment(department);
            userRepository.save(manager);
        }
    }
}
