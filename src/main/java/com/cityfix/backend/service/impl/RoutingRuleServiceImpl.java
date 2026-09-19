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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoutingRuleServiceImpl implements RoutingRuleService {

    private final RoutingRuleRepository routingRuleRepository;
    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;
    private final RoutingRuleMapper routingRuleMapper;

    @Override
    @Transactional
    public RoutingRuleResponse createRoutingRule(CreateRoutingRuleRequest request) {
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        boolean exists = routingRuleRepository.existsByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(
                request.categoryId(), request.province(), request.ward());
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
        if (request.departmentId() != null && !request.departmentId().equals(department.getDepartmentId())) {
            department = departmentRepository.findByDepartmentIdAndIsDeletedFalse(request.departmentId())
                    .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));
        }

        Category category = routingRule.getCategory();
        if (request.categoryId() != null && !request.categoryId().equals(category.getCategoryId())) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        UUID targetCategoryId = category.getCategoryId();
        String targetProvince = request.province() != null ? request.province() : routingRule.getProvince();
        String targetWard = request.ward() != null ? request.ward() : routingRule.getWard();

        boolean conflict = routingRuleRepository.existsByCategoryCategoryIdAndProvinceAndWardAndRuleIdNotAndIsDeletedFalse(
                targetCategoryId, targetProvince, targetWard, id);
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
        if (!departmentRepository.existsById(departmentId)) {
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
