package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.RoutingRuleMapper;
import com.cityfix.backend.model.dto.request.CreateRoutingRuleRequest;
import com.cityfix.backend.model.dto.request.UpdateRoutingRuleRequest;
import com.cityfix.backend.model.dto.response.RoutingRuleResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.RoutingRule;
import com.cityfix.backend.repository.CategoryRepository;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.repository.RoutingRuleRepository;
import com.cityfix.backend.service.RoutingRuleService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoutingRuleServiceImpl implements RoutingRuleService {

    private final RoutingRuleRepository routingRuleRepository;
    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;
    private final RoutingRuleMapper routingRuleMapper;

    private Department resolveActiveDepartment(UUID departmentId) {
        if (departmentId == null) {
            throw new AppException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        Department department = departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)
                .or(() -> departmentRepository.findById(departmentId).filter(d -> !d.isDeleted()))
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));
        if (department.isDeleted()) {
            throw new AppException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        return department;
    }

    private Category resolveActiveCategory(UUID categoryId) {
        if (categoryId == null) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        Category category = categoryRepository.findByCategoryIdAndIsDeletedFalse(categoryId)
                .or(() -> categoryRepository.findById(categoryId).filter(c -> !c.isDeleted()))
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        if (category.isDeleted() || !category.isActive()) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
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

    private boolean hasOverlappingActiveRule(UUID categoryId, String province, String ward, UUID excludeRuleId) {
        if (categoryId == null) {
            return false;
        }
        List<RoutingRule> existingRules = routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(categoryId);
        if (existingRules == null || existingRules.isEmpty()) {
            return false;
        }
        String normProvince = normalizeLocationName(province, "PROVINCE");
        String normWard = normalizeLocationName(ward, "WARD");

        return existingRules.stream()
                .filter(Objects::nonNull)
                .filter(r -> !r.isDeleted())
                .filter(r -> excludeRuleId == null || !Objects.equals(r.getRuleId(), excludeRuleId))
                .anyMatch(r -> {
                    String rProvince = normalizeLocationName(r.getProvince(), "PROVINCE");
                    String rWard = normalizeLocationName(r.getWard(), "WARD");
                    return normProvince.equals(rProvince) && normWard.equals(rWard);
                });
    }

    @Override
    @Transactional
    public RoutingRuleResponse createRoutingRule(CreateRoutingRuleRequest request) {
        Department department = resolveActiveDepartment(request.departmentId());
        Category category = resolveActiveCategory(request.categoryId());

        boolean exists = routingRuleRepository.existsByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(
                request.categoryId(), request.province(), request.ward())
                || hasOverlappingActiveRule(request.categoryId(), request.province(), request.ward(), null);
        if (exists) {
            throw new AppException(ErrorCode.ROUTING_RULE_CONFLICT);
        }

        RoutingRule routingRule = routingRuleMapper.toEntity(request);
        routingRule.setDepartment(department);
        routingRule.setCategory(category);

        RoutingRule savedRule = routingRuleRepository.save(routingRule);
        return routingRuleMapper.toResponse(savedRule);
    }

    @Override
    @Transactional
    public RoutingRuleResponse updateRoutingRule(UUID id, UpdateRoutingRuleRequest request) {
        RoutingRule routingRule = routingRuleRepository.findByRuleIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROUTING_RULE_NOT_FOUND));

        Department department = routingRule.getDepartment();
        UUID currentDepartmentId = department != null ? department.getDepartmentId() : null;
        if (request.departmentId() != null && !Objects.equals(request.departmentId(), currentDepartmentId)) {
            department = resolveActiveDepartment(request.departmentId());
        } else if (department != null && department.isDeleted()) {
            throw new AppException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        Category category = routingRule.getCategory();
        UUID currentCategoryId = category != null ? category.getCategoryId() : null;
        if (request.categoryId() != null && !Objects.equals(request.categoryId(), currentCategoryId)) {
            category = resolveActiveCategory(request.categoryId());
        } else if (category != null && (category.isDeleted() || !category.isActive())) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        UUID targetCategoryId = category != null ? category.getCategoryId() : null;
        String targetProvince = request.province() != null ? request.province() : routingRule.getProvince();
        String targetWard = request.ward() != null ? request.ward() : routingRule.getWard();

        boolean conflict = routingRuleRepository.existsByCategoryCategoryIdAndProvinceAndWardAndRuleIdNotAndIsDeletedFalse(
                targetCategoryId, targetProvince, targetWard, id)
                || hasOverlappingActiveRule(targetCategoryId, targetProvince, targetWard, id);
        if (conflict) {
            throw new AppException(ErrorCode.ROUTING_RULE_CONFLICT);
        }

        routingRuleMapper.updateEntityFromRequest(request, routingRule);
        routingRule.setDepartment(department);
        routingRule.setCategory(category);

        RoutingRule updatedRule = routingRuleRepository.save(routingRule);
        return routingRuleMapper.toResponse(updatedRule);
    }

    @Override
    @Transactional(readOnly = true)
    public RoutingRuleResponse getRoutingRuleById(UUID id) {
        RoutingRule routingRule = routingRuleRepository.findByRuleIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROUTING_RULE_NOT_FOUND));

        return routingRuleMapper.toResponse(routingRule);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RoutingRuleResponse> searchRoutingRules(UUID categoryId, String province, String ward, UUID departmentId, Pageable pageable) {
        Specification<RoutingRule> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("isDeleted"), false));

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("categoryId"), categoryId));
            }
            if (province != null && !province.isBlank()) {
                predicates.add(cb.equal(root.get("province"), province));
            }
            if (ward != null && !ward.isBlank()) {
                predicates.add(cb.equal(root.get("ward"), ward));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("departmentId"), departmentId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RoutingRule> page = routingRuleRepository.findAll(spec, pageable);
        return page.map(routingRuleMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutingRuleResponse> getRoutingRulesByDepartment(UUID departmentId) {
        if (!departmentRepository.existsByDepartmentIdAndIsDeletedFalse(departmentId)) {
            throw new AppException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        List<RoutingRule> rules = routingRuleRepository.findByDepartmentDepartmentIdAndIsDeletedFalse(departmentId);
        return routingRuleMapper.toResponseList(rules);
    }

    @Override
    @Transactional
    public void deleteRoutingRule(UUID id) {
        RoutingRule routingRule = routingRuleRepository.findByRuleIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROUTING_RULE_NOT_FOUND));

        routingRule.setDeleted(true);
        routingRule.setDeletedAt(LocalDateTime.now());
        routingRuleRepository.save(routingRule);
    }
}
