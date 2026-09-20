package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.CreateRoutingRuleRequest;
import com.cityfix.backend.model.dto.request.UpdateRoutingRuleRequest;
import com.cityfix.backend.model.dto.response.RoutingRuleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface RoutingRuleService {
    RoutingRuleResponse createRoutingRule(CreateRoutingRuleRequest request);
    RoutingRuleResponse updateRoutingRule(UUID id, UpdateRoutingRuleRequest request);
    RoutingRuleResponse getRoutingRuleById(UUID id);
    Page<RoutingRuleResponse> searchRoutingRules(UUID categoryId, String province, String ward, UUID departmentId, Pageable pageable);
    List<RoutingRuleResponse> getRoutingRulesByDepartment(UUID departmentId);
    void deleteRoutingRule(UUID id);
}
