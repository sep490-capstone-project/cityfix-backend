package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.RoutingRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID>, JpaSpecificationExecutor<RoutingRule> {

    boolean existsByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(UUID categoryId, String province, String ward);

    boolean existsByCategoryCategoryIdAndProvinceAndWardAndRuleIdNotAndIsDeletedFalse(UUID categoryId, String province, String ward, UUID ruleId);

    Optional<RoutingRule> findByRuleIdAndIsDeletedFalse(UUID ruleId);

    Optional<RoutingRule> findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(UUID categoryId, String province, String ward);

    List<RoutingRule> findByDepartmentDepartmentId(UUID departmentId);

    List<RoutingRule> findByDepartmentDepartmentIdAndIsDeletedFalse(UUID departmentId);

    Page<RoutingRule> findByDepartmentDepartmentId(UUID departmentId, Pageable pageable);
    
    List<RoutingRule> findByCategoryCategoryIdAndIsDeletedFalse(UUID categoryId);

    List<RoutingRule> findByCategoryCategoryIdInAndIsDeletedFalse(java.util.Collection<UUID> categoryIds);
}
