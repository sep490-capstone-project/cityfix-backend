package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.request.AdminUserRequest;
import com.cityfix.backend.model.dto.response.AdminUserResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.UserDepartment;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.repository.RoleRepository;
import com.cityfix.backend.repository.UserDepartmentRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAllByIsDeletedFalse().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AdminUserResponse createUser(AdminUserRequest request) {
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        if ("ADMIN".equalsIgnoreCase(role.getRoleName())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Cannot create user with ADMIN role through this API");
        }

        userRepository.findByEmailAndIsDeletedFalse(request.getEmail()).ifPresent(existing -> {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        });

        userRepository.findByPhoneNumberAndIsDeletedFalse(request.getPhoneNumber()).ifPresent(existing -> {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        });

        List<Department> departments = resolveDepartments(request.getDepartmentId(), request.getDepartmentIds());
        Department primaryDepartment = departments.isEmpty() ? null : departments.get(0);

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Password is required for creating a new user");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .province(request.getProvince())
                .ward(request.getWard())
                .address(request.getAddress())
                .role(role)
                .department(primaryDepartment)
                .status(request.getStatus() != null ? request.getStatus() : UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        // Đồng bộ N-N vào bảng user_department nếu role là STAFF hoặc MANAGER
        if (isStaffOrManager(role)) {
            for (Department dept : departments) {
                UserDepartment ud = UserDepartment.builder()
                        .user(savedUser)
                        .department(dept)
                        .build();
                userDepartmentRepository.save(ud);
            }
        }

        return mapToResponse(savedUser);
    }

    @Override
    @Transactional
    public AdminUserResponse updateUser(UUID id, AdminUserRequest request) {
        User user = userRepository.findByUserIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if ("ADMIN".equalsIgnoreCase(user.getRole().getRoleName())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Cannot update an ADMIN user");
        }

        Role newRole = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        if ("ADMIN".equalsIgnoreCase(newRole.getRoleName())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Cannot change role to ADMIN");
        }
        
        userRepository.findByEmailAndIsDeletedFalse(request.getEmail()).ifPresent(existing -> {
            if (!existing.getUserId().equals(id)) {
                throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
        });

        userRepository.findByPhoneNumberAndIsDeletedFalse(request.getPhoneNumber()).ifPresent(existing -> {
            if (!existing.getUserId().equals(id)) {
                throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
        });

        if (!user.getEmail().equals(request.getEmail())) {
            user.setEmail(request.getEmail());
            user.setEmailVerified(false);
        }

        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setProvince(request.getProvince());
        user.setWard(request.getWard());
        user.setAddress(request.getAddress());
        user.setRole(newRole);

        // Xử lý đồng bộ phòng ban N-N
        if (!isStaffOrManager(newRole)) {
            // Nếu đổi role sang RESIDENT, gỡ bỏ toàn bộ liên kết phòng ban
            userDepartmentRepository.deleteByUserUserId(user.getUserId());
            user.setDepartment(null);
        } else {
            boolean hasDeptInput = request.getDepartmentIds() != null || request.getDepartmentId() != null;
            if (hasDeptInput) {
                List<Department> departments = resolveDepartments(request.getDepartmentId(), request.getDepartmentIds());
                Department primaryDepartment = departments.isEmpty() ? null : departments.get(0);
                user.setDepartment(primaryDepartment);
                syncUserDepartments(user, departments);
            }
        }
        
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findByUserIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if ("ADMIN".equalsIgnoreCase(user.getRole().getRoleName())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Cannot delete an ADMIN user");
        }

        // Xóa liên kết user_department khi user bị xóa
        userDepartmentRepository.deleteByUserUserId(id);

        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private List<Department> resolveDepartments(UUID singleDeptId, List<UUID> multiDeptIds) {
        Set<UUID> targetIds = new LinkedHashSet<>();
        if (multiDeptIds != null && !multiDeptIds.isEmpty()) {
            targetIds.addAll(multiDeptIds);
        } else if (singleDeptId != null) {
            targetIds.add(singleDeptId);
        }

        if (targetIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Department> departments = departmentRepository.findAllById(targetIds).stream()
                .filter(d -> !d.isDeleted())
                .collect(Collectors.toList());

        if (departments.size() != targetIds.size()) {
            throw new AppException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        return departments;
    }

    private boolean isStaffOrManager(Role role) {
        if (role == null || role.getRoleName() == null) return false;
        String r = role.getRoleName().toUpperCase();
        return "STAFF".equals(r) || "ROLE_STAFF".equals(r) || "MANAGER".equals(r) || "ROLE_MANAGER".equals(r);
    }

    private void syncUserDepartments(User user, List<Department> targetDepartments) {
        Set<UUID> targetIds = targetDepartments.stream()
                .map(Department::getDepartmentId)
                .collect(Collectors.toSet());

        List<UserDepartment> currentMappings = userDepartmentRepository.findByUserUserId(user.getUserId());
        
        // Xóa các liên kết không còn được chọn
        for (UserDepartment ud : currentMappings) {
            if (ud.getDepartment() != null && !targetIds.contains(ud.getDepartment().getDepartmentId())) {
                userDepartmentRepository.delete(ud);
            }
        }

        Set<UUID> existingIds = currentMappings.stream()
                .filter(ud -> ud.getDepartment() != null)
                .map(ud -> ud.getDepartment().getDepartmentId())
                .collect(Collectors.toSet());

        // Thêm các liên kết mới
        for (Department dept : targetDepartments) {
            if (!existingIds.contains(dept.getDepartmentId())) {
                UserDepartment ud = UserDepartment.builder()
                        .user(user)
                        .department(dept)
                        .build();
                userDepartmentRepository.save(ud);
            }
        }
    }

    private AdminUserResponse mapToResponse(User user) {
        List<Department> userDepts = userDepartmentRepository.findDepartmentsByUserId(user.getUserId());
        if (userDepts.isEmpty() && user.getDepartment() != null) {
            userDepts = List.of(user.getDepartment());
        }

        List<AdminUserResponse.DepartmentSummaryResponse> deptSummaries = userDepts.stream()
                .map(d -> AdminUserResponse.DepartmentSummaryResponse.builder()
                        .departmentId(d.getDepartmentId())
                        .departmentName(d.getName())
                        .build())
                .collect(Collectors.toList());

        return AdminUserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .province(user.getProvince())
                .ward(user.getWard())
                .address(user.getAddress())
                .roleName(user.getRole() != null ? user.getRole().getRoleName() : null)
                .roleId(user.getRole() != null ? user.getRole().getRoleId() : null)
                .departmentId(user.getDepartment() != null ? user.getDepartment().getDepartmentId() : null)
                .departments(deptSummaries)
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
