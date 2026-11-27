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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));

        boolean isReactivating = department.isDeleted() && Boolean.TRUE.equals(request.isActive());
        if (department.isDeleted() && !isReactivating) {
            throw new AppException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        String targetName = request.name() != null ? request.name() : department.getName();
        boolean nameChanged = request.name() != null && !request.name().equals(department.getName());
        if ((nameChanged || isReactivating) && targetName != null) {
            if (departmentRepository.existsByNameAndDepartmentIdNotAndIsDeletedFalse(targetName, id)) {
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

        return userDepartmentRepository.findDepartmentUsersWithRole(departmentId).stream()
                .map(departmentMapper::toDepartmentUserResponse)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentUserResponse> getDepartmentStaffBySubCategoryIds(List<UUID> subCategoryIds, String province, String ward) {
        if (subCategoryIds == null || subCategoryIds.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Set<UUID> distinctSubCategoryIds = subCategoryIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (distinctSubCategoryIds.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Set<UUID> categoryIds = new LinkedHashSet<>();
        for (UUID subCatId : distinctSubCategoryIds) {
            SubCategory subCategory = subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId)
                    .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));

            if (!subCategory.isActive() || subCategory.isDeleted()
                    || subCategory.getCategory() == null || subCategory.getCategory().isDeleted()) {
                throw new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND);
            }
            categoryIds.add(subCategory.getCategory().getCategoryId());
        }

        Set<UUID> matchedDepartmentIds = categoryIds.stream()
                .flatMap(catId -> findMatchingDepartmentIdsForCategory(catId, province, ward).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (matchedDepartmentIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, DepartmentUserResponse> staffMap = new LinkedHashMap<>();
        for (UUID departmentId : matchedDepartmentIds) {
            List<UserDepartment> mappings = userDepartmentRepository.findDepartmentUsersWithRole(departmentId);
            if (mappings == null) continue;
            for (UserDepartment ud : mappings) {
                if (isActiveStaffMapping(ud)) {
                    UUID userId = ud.getUser().getUserId();
                    staffMap.computeIfAbsent(userId, k -> departmentMapper.toDepartmentUserResponse(ud));
                }
            }
        }

        return new ArrayList<>(staffMap.values());
    }

    private boolean isActiveStaffMapping(UserDepartment ud) {
        if (ud == null || ud.getUser() == null || ud.getUser().getUserId() == null) {
            return false;
        }
        User u = ud.getUser();
        return !u.isDeleted()
                && u.getStatus() == UserStatus.ACTIVE
                && u.getRole() != null
                && "STAFF".equalsIgnoreCase(u.getRole().getRoleName());
    }

    private Set<UUID> findMatchingDepartmentIdsForCategory(UUID categoryId, String province, String ward) {
        boolean hasProvince = province != null && !province.isBlank();
        boolean hasWard = ward != null && !ward.isBlank();

        String normProvince = hasProvince ? normalizeLocationName(province, "PROVINCE") : "";
        String normWard = hasWard ? normalizeLocationName(ward, "WARD") : "";

        List<RoutingRule> categoryRules = routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(categoryId);
        if (categoryRules == null || categoryRules.isEmpty()) {
            return Collections.emptySet();
        }

        List<RoutingRule> validRules = categoryRules.stream()
                .filter(Objects::nonNull)
                .filter(r -> !r.isDeleted()
                        && r.getDepartment() != null
                        && !r.getDepartment().isDeleted()
                        && r.getDepartment().getDepartmentId() != null)
                .toList();

        if (validRules.isEmpty()) {
            return Collections.emptySet();
        }

        // Trường hợp 1: Không truyền cả province và ward -> lấy toàn bộ departmentId của category
        if (!hasProvince && !hasWard) {
            return validRules.stream()
                    .map(r -> r.getDepartment().getDepartmentId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        // Trường hợp 2: Chỉ truyền province -> lấy tất cả departmentId có rule.province khớp normProvince
        if (hasProvince && !hasWard) {
            return validRules.stream()
                    .filter(r -> {
                        String ruleProvince = normalizeLocationName(r.getProvince(), "PROVINCE");
                        return !ruleProvince.isEmpty() && normProvince.equalsIgnoreCase(ruleProvince);
                    })
                    .map(r -> r.getDepartment().getDepartmentId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        // Trường hợp 4: Chỉ truyền ward (không truyền province) -> lấy tất cả departmentId có rule.ward khớp normWard ở mọi tỉnh
        if (!hasProvince && hasWard) {
            return validRules.stream()
                    .filter(r -> {
                        String ruleWard = normalizeLocationName(r.getWard(), "WARD");
                        return !ruleWard.isEmpty() && normWard.equalsIgnoreCase(ruleWard);
                    })
                    .map(r -> r.getDepartment().getDepartmentId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        // Trường hợp 3: Truyền cả province và ward -> ưu tiên khớp cả province + ward; nếu rỗng thì fallback rule cấp tỉnh (ward null/blank)
        Set<UUID> wardMatchedDeptIds = validRules.stream()
                .filter(r -> {
                    String ruleProvince = normalizeLocationName(r.getProvince(), "PROVINCE");
                    String ruleWard = normalizeLocationName(r.getWard(), "WARD");
                    return !ruleProvince.isEmpty() && !ruleWard.isEmpty()
                            && normProvince.equalsIgnoreCase(ruleProvince)
                            && normWard.equalsIgnoreCase(ruleWard);
                })
                .map(r -> r.getDepartment().getDepartmentId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!wardMatchedDeptIds.isEmpty()) {
            return wardMatchedDeptIds;
        }

        return validRules.stream()
                .filter(r -> r.getWard() == null || r.getWard().isBlank())
                .filter(r -> {
                    String ruleProvince = normalizeLocationName(r.getProvince(), "PROVINCE");
                    return !ruleProvince.isEmpty() && normProvince.equalsIgnoreCase(ruleProvince);
                })
                .map(r -> r.getDepartment().getDepartmentId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
