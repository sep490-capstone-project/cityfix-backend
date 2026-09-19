package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.request.CreateRoutingRuleRequest;
import com.cityfix.backend.model.dto.request.UpdateRoutingRuleRequest;
import com.cityfix.backend.model.dto.response.RoutingRuleResponse;
import com.cityfix.backend.model.entity.RoutingRule;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RoutingRuleMapper {

    @Mapping(target = "department", ignore = true)
    @Mapping(target = "category", ignore = true)
    RoutingRule toEntity(CreateRoutingRuleRequest request);

    @Mapping(target = "departmentId", source = "department.departmentId")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "categoryId", source = "category.categoryId")
    @Mapping(target = "categoryName", source = "category.name")
    RoutingRuleResponse toResponse(RoutingRule routingRule);

    List<RoutingRuleResponse> toResponseList(List<RoutingRule> routingRules);

    @Mapping(target = "department", ignore = true)
    @Mapping(target = "category", ignore = true)
    void updateEntityFromRequest(UpdateRoutingRuleRequest request, @MappingTarget RoutingRule routingRule);
}
