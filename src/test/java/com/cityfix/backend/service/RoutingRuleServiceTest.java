package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.RoutingRuleMapper;
import com.cityfix.backend.model.dto.request.CreateRoutingRuleRequest;
import com.cityfix.backend.model.dto.request.UpdateRoutingRuleRequest;
import com.cityfix.backend.model.dto.response.RoutingRuleResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.RoutingRule;
import com.cityfix.backend.model.enums.DepartmentLevel;
import com.cityfix.backend.repository.CategoryRepository;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.repository.RoutingRuleRepository;
import com.cityfix.backend.service.impl.RoutingRuleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingRuleServiceTest {

    @Mock
    private RoutingRuleRepository routingRuleRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private RoutingRuleMapper routingRuleMapper;

    @InjectMocks
    private RoutingRuleServiceImpl routingRuleService;

    private UUID ruleId;
    private UUID departmentId;
    private UUID categoryId;
    private Department mockDepartment;
    private Category mockCategory;
    private RoutingRule mockRule;
    private RoutingRuleResponse mockResponse;
    private CreateRoutingRuleRequest createRequest;
    private UpdateRoutingRuleRequest updateRequest;

    @BeforeEach
    void setUp() {
        ruleId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        mockDepartment = Department.builder()
                .departmentId(departmentId)
                .name("Phòng xử lý sự cố hạ tầng phường Long Trường")
                .level(DepartmentLevel.WARD)
                .build();

        mockCategory = Category.builder()
                .categoryId(categoryId)
                .name("Hạ tầng giao thông")
                .build();

        mockRule = RoutingRule.builder()
                .ruleId(ruleId)
                .department(mockDepartment)
                .category(mockCategory)
                .province("Thành phố Hồ Chí Minh")
                .ward("Phường Long Trường")
                .build();

        mockResponse = new RoutingRuleResponse(
                ruleId,
                departmentId,
                "Phòng xử lý sự cố hạ tầng phường Long Trường",
                categoryId,
                "Hạ tầng giao thông",
                "Thành phố Hồ Chí Minh",
                "Phường Long Trường",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        createRequest = new CreateRoutingRuleRequest(
                departmentId,
                categoryId,
                "Thành phố Hồ Chí Minh",
                "Phường Long Trường"
        );

        updateRequest = new UpdateRoutingRuleRequest(
                departmentId,
                categoryId,
                "Thành phố Hồ Chí Minh",
                "Phường Trường Thạnh"
        );
    }

    @Test
    @DisplayName("createRoutingRule_validData_returnsRoutingRuleResponse")
    void createRoutingRule_validData_returnsRoutingRuleResponse() {
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
        when(routingRuleRepository.existsByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(
                categoryId, createRequest.province(), createRequest.ward())).thenReturn(false);
        when(routingRuleMapper.toEntity(createRequest)).thenReturn(mockRule);
        when(routingRuleRepository.save(any(RoutingRule.class))).thenReturn(mockRule);
        when(routingRuleMapper.toResponse(mockRule)).thenReturn(mockResponse);

        RoutingRuleResponse result = routingRuleService.createRoutingRule(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.categoryId()).isEqualTo(categoryId);

        verify(routingRuleRepository, times(1)).save(any(RoutingRule.class));
    }

    @Test
    @DisplayName("createRoutingRule_duplicateRule_throwsRoutingRuleConflictException")
    void createRoutingRule_duplicateRule_throwsRoutingRuleConflictException() {
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
        when(routingRuleRepository.existsByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(
                categoryId, createRequest.province(), createRequest.ward())).thenReturn(true);

        assertThatThrownBy(() -> routingRuleService.createRoutingRule(createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROUTING_RULE_CONFLICT);

        verify(routingRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRoutingRule_departmentNotFound_throwsDepartmentNotFoundException")
    void createRoutingRule_departmentNotFound_throwsDepartmentNotFoundException() {
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routingRuleService.createRoutingRule(createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);

        verify(routingRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("getRoutingRuleById_validId_returnsRoutingRuleResponse")
    void getRoutingRuleById_validId_returnsRoutingRuleResponse() {
        when(routingRuleRepository.findById(ruleId)).thenReturn(Optional.of(mockRule));
        when(routingRuleMapper.toResponse(mockRule)).thenReturn(mockResponse);

        RoutingRuleResponse result = routingRuleService.getRoutingRuleById(ruleId);

        assertThat(result).isNotNull();
        assertThat(result.ruleId()).isEqualTo(ruleId);
    }

    @Test
    @DisplayName("getRoutingRulesByDepartment_validDepartmentId_returnsList")
    void getRoutingRulesByDepartment_validDepartmentId_returnsList() {
        when(departmentRepository.existsById(departmentId)).thenReturn(true);
        when(routingRuleRepository.findByDepartmentDepartmentId(departmentId)).thenReturn(List.of(mockRule));
        when(routingRuleMapper.toResponseList(List.of(mockRule))).thenReturn(List.of(mockResponse));

        List<RoutingRuleResponse> result = routingRuleService.getRoutingRulesByDepartment(departmentId);

        assertThat(result).hasSize(1);
        verify(routingRuleRepository, times(1)).findByDepartmentDepartmentId(departmentId);
    }

    @Test
    @DisplayName("deleteRoutingRule_validId_softDeletesEntity")
    void deleteRoutingRule_validId_softDeletesEntity() {
        when(routingRuleRepository.findById(ruleId)).thenReturn(Optional.of(mockRule));

        routingRuleService.deleteRoutingRule(ruleId);

        verify(routingRuleRepository, times(1)).save(mockRule);
        assertThat(mockRule.isDeleted()).isTrue();
        assertThat(mockRule.getDeletedAt()).isNotNull();
    }
}
