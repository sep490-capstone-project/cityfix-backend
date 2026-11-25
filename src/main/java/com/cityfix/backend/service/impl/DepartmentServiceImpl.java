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
import com.cityfix.backend.model.entity.RoutingRule;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.UserDepartment;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.repository.RoutingRuleRepository;
import com.cityfix.backend.repository.SubCategoryRepository;
import com.cityfix.backend.repository.UserDepartmentRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final RoutingRuleRepository routingRuleRepository;
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
            }
        }

        return getDepartmentUsers(departmentId);
    }

    @Override
    @Transactional
    public void removeUserFromDepartment(UUID departmentId, UUID userId) {
        departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));

        userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        userDepartmentRepository.deleteByUserUserIdAndDepartmentDepartmentId(userId, departmentId);
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

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentUserResponse> getDepartmentStaffBySubCategoryId(UUID subCategoryId, String province, String ward) {
        if (subCategoryId == null || province == null || province.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        SubCategory subCategory = subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCategoryId)
                .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));

        if (!subCategory.isActive() || subCategory.getCategory() == null || subCategory.getCategory().isDeleted()) {
            throw new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND);
        }

        UUID categoryId = subCategory.getCategory().getCategoryId();
        Optional<RoutingRule> matchedRuleOpt = findMatchingRoutingRule(categoryId, province, ward);
        if (matchedRuleOpt.isEmpty() || matchedRuleOpt.get().getDepartment() == null
                || matchedRuleOpt.get().getDepartment().isDeleted()) {
            return Collections.emptyList();
        }

        UUID departmentId = matchedRuleOpt.get().getDepartment().getDepartmentId();
        List<UserDepartment> mappings = userDepartmentRepository.findDepartmentUsersWithRole(departmentId);

        List<DepartmentUserResponse> staffList = new ArrayList<>();
        Set<UUID> seenUserIds = new LinkedHashSet<>();

        if (mappings != null && !mappings.isEmpty()) {
            for (UserDepartment ud : mappings) {
                if (ud == null || ud.getUser() == null) continue;
                User u = ud.getUser();
                String roleName = u.getRole() != null ? u.getRole().getRoleName() : null;
                if (!u.isDeleted()
                        && u.getStatus() == UserStatus.ACTIVE
                        && roleName != null
                        && "STAFF".equalsIgnoreCase(roleName)
                        && u.getUserId() != null
                        && seenUserIds.add(u.getUserId())) {
                    staffList.add(DepartmentUserResponse.builder()
                            .userId(u.getUserId())
                            .email(u.getEmail())
                            .fullName(u.getFullName())
                            .phoneNumber(u.getPhoneNumber())
                            .roleName(roleName)
                            .status(u.getStatus())
                            .joinedAt(ud.getCreatedAt())
                            .build());
                }
            }
        } else {
            List<User> activeStaffs = userDepartmentRepository.findActiveStaffByDepartmentId(departmentId);
            if (activeStaffs != null) {
                for (User u : activeStaffs) {
                    if (u != null && !u.isDeleted() && u.getStatus() == UserStatus.ACTIVE
                            && u.getUserId() != null && seenUserIds.add(u.getUserId())) {
                        staffList.add(DepartmentUserResponse.builder()
                                .userId(u.getUserId())
                                .email(u.getEmail())
                                .fullName(u.getFullName())
                                .phoneNumber(u.getPhoneNumber())
                                .roleName(u.getRole() != null ? u.getRole().getRoleName() : "STAFF")
                                .status(u.getStatus())
                                .build());
                    }
                }
            }
        }

        return staffList;
    }

    private Optional<RoutingRule> findMatchingRoutingRule(UUID categoryId, String province, String ward) {
        String trimmedProvince = province != null ? province.trim() : null;
        String trimmedWard = (ward != null && !ward.isBlank()) ? ward.trim() : null;

        if (trimmedProvince != null && trimmedWard != null) {
            Optional<RoutingRule> exactOpt = routingRuleRepository
                    .findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(categoryId, trimmedProvince, trimmedWard);
            if (exactOpt.isPresent() && exactOpt.get().getDepartment() != null && !exactOpt.get().getDepartment().isDeleted()) {
                return exactOpt;
            }
        }

        String normProvince = normalizeLocationName(trimmedProvince, "PROVINCE");
        String normWard = normalizeLocationName(trimmedWard, "WARD");

        List<RoutingRule> categoryRules = routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(categoryId);
        if (categoryRules != null && !categoryRules.isEmpty()) {
            if (!normProvince.isEmpty() && !normWard.isEmpty()) {
                Optional<RoutingRule> wardRuleOpt = categoryRules.stream()
                        .filter(Objects::nonNull)
                        .filter(r -> !r.isDeleted() && r.getDepartment() != null && !r.getDepartment().isDeleted())
                        .filter(r -> r.getProvince() != null && !r.getProvince().isBlank()
                                && r.getWard() != null && !r.getWard().isBlank())
                        .filter(r -> {
                            String ruleProvince = normalizeLocationName(r.getProvince(), "PROVINCE");
                            String ruleWard = normalizeLocationName(r.getWard(), "WARD");
                            return normProvince.equalsIgnoreCase(ruleProvince)
                                    && normWard.equalsIgnoreCase(ruleWard);
                        })
                        .findFirst();
                if (wardRuleOpt.isPresent()) {
                    return wardRuleOpt;
                }
            }

            if (!normProvince.isEmpty()) {
                Optional<RoutingRule> provinceFallbackOpt = categoryRules.stream()
                        .filter(Objects::nonNull)
                        .filter(r -> !r.isDeleted() && r.getDepartment() != null && !r.getDepartment().isDeleted())
                        .filter(r -> r.getProvince() != null && !r.getProvince().isBlank()
                                && (r.getWard() == null || r.getWard().isBlank()))
                        .filter(r -> {
                            String ruleProvince = normalizeLocationName(r.getProvince(), "PROVINCE");
                            return normProvince.equalsIgnoreCase(ruleProvince);
                        })
                        .findFirst();
                if (provinceFallbackOpt.isPresent()) {
                    return provinceFallbackOpt;
                }
            }
        }

        if (trimmedProvince != null) {
            Optional<RoutingRule> exactProvinceOnly = routingRuleRepository
                    .findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(categoryId, trimmedProvince, null);
            if (exactProvinceOnly.isPresent() && exactProvinceOnly.get().getDepartment() != null
                    && !exactProvinceOnly.get().getDepartment().isDeleted()) {
                return exactProvinceOnly;
            }
        }

        return Optional.empty();
    }

    private String normalizeLocationName(String rawName, String type) {
        if (rawName == null) {
            return "";
        }
        String cleaned = rawName.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return "";
        }
        if ("PROVINCE".equalsIgnoreCase(type)) {
            cleaned = cleaned.replaceFirst("(?iu)^(thành\\s+phố|tp\\.|tp|tỉnh)\\s*", "").trim();
            if ("HCM".equalsIgnoreCase(cleaned) || "TPHCM".equalsIgnoreCase(cleaned)) {
                cleaned = "Hồ Chí Minh";
            }
        } else if ("WARD".equalsIgnoreCase(type)) {
            cleaned = cleaned.replaceFirst("(?iu)^(phường\\s+|p\\.\\s*|xã\\s+|thị\\s+trấn\\s+|tt\\.\\s*)", "").trim();
        }
        return cleaned.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
