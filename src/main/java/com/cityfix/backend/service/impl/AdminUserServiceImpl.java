package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.request.AdminUserRequest;
import com.cityfix.backend.model.dto.response.AdminUserResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.repository.RoleRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAllByIsDeletedFalse().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AdminUserResponse createUser(AdminUserRequest request) {
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        if ("ADMIN".equalsIgnoreCase(role.getRoleName())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Cannot create user with ADMIN role through this API");
        }

        userRepository.findByEmailAndIsDeletedFalse(request.getEmail()).ifPresent(existing -> {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        });

        userRepository.findByPhoneNumber(request.getPhoneNumber()).ifPresent(existing -> {
            if (!existing.isDeleted()) {
                throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
        });

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));
        }

        if (request.getPassword() == null || request.getPassword().isEmpty()) {
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
                .department(department)
                .status(request.getStatus() != null ? request.getStatus() : UserStatus.ACTIVE)
                .build();

        if ("STAFF".equalsIgnoreCase(role.getRoleName())) {
            user.setIsDispatcher(Boolean.TRUE.equals(request.getIsDispatcher()));
        } else {
            user.setIsDispatcher(false);
        }

        User savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    @Override
    public AdminUserResponse updateUser(UUID id, AdminUserRequest request) {
        User user = userRepository.findByIdAndIsDeletedFalse(id)
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

        userRepository.findByPhoneNumber(request.getPhoneNumber()).ifPresent(existing -> {
            if (!existing.isDeleted() && !existing.getUserId().equals(id)) {
                throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
        });

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));
        }

        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setProvince(request.getProvince());
        user.setWard(request.getWard());
        user.setAddress(request.getAddress());
        user.setRole(newRole);
        user.setDepartment(department);
        
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        if ("STAFF".equalsIgnoreCase(newRole.getRoleName())) {
            user.setIsDispatcher(Boolean.TRUE.equals(request.getIsDispatcher()));
        } else {
            user.setIsDispatcher(false);
        }

        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    @Override
    public void deleteUser(UUID id) {
        User user = userRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if ("ADMIN".equalsIgnoreCase(user.getRole().getRoleName())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Cannot delete an ADMIN user");
        }

        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private AdminUserResponse mapToResponse(User user) {
        return AdminUserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .province(user.getProvince())
                .ward(user.getWard())
                .address(user.getAddress())
                .roleName(user.getRole().getRoleName())
                .roleId(user.getRole().getRoleId())
                .departmentId(user.getDepartment() != null ? user.getDepartment().getDepartmentId() : null)
                .status(user.getStatus())
                .isDispatcher(user.getIsDispatcher())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
