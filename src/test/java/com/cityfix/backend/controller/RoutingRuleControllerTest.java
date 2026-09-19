package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.CreateRoutingRuleRequest;
import com.cityfix.backend.model.dto.request.UpdateRoutingRuleRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.RoutingRuleResponse;
import com.cityfix.backend.service.RoutingRuleService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingRuleControllerTest {

    @Mock
    private RoutingRuleService routingRuleService;

    @InjectMocks
    private RoutingRuleController routingRuleController;

    private static Validator validator;

    private UUID ruleId;
    private UUID departmentId;
    private UUID categoryId;
    private CreateRoutingRuleRequest validCreateRequest;
    private RoutingRuleResponse mockResponse;

    @BeforeAll
    static void initValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @BeforeEach
    void setUp() {
        ruleId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        validCreateRequest = new CreateRoutingRuleRequest(
                departmentId,
                categoryId,
                "Thành phố Hồ Chí Minh",
                "Phường Long Trường"
        );

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
    }

    @Test
    @DisplayName("createRoutingRule_validRequest_returns200OK")
    void createRoutingRule_validRequest_returns200OK() {
        when(routingRuleService.createRoutingRule(any(CreateRoutingRuleRequest.class))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<RoutingRuleResponse>> response = routingRuleController.createRoutingRule(validCreateRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().ruleId()).isEqualTo(ruleId);

        verify(routingRuleService, times(1)).createRoutingRule(validCreateRequest);
    }

    @Test
    @DisplayName("validation_missingDepartmentId_producesConstraintViolation")
    void validation_missingDepartmentId_producesConstraintViolation() {
        CreateRoutingRuleRequest invalidRequest = new CreateRoutingRuleRequest(
                null,
                categoryId,
                "Thành phố Hồ Chí Minh",
                "Phường Long Trường"
        );

        Set<ConstraintViolation<CreateRoutingRuleRequest>> violations = validator.validate(invalidRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Phòng ban không được để trống"));
    }

    @Test
    @DisplayName("searchRoutingRules_validParams_returns200OKWithPage")
    void searchRoutingRules_validParams_returns200OKWithPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<RoutingRuleResponse> mockPage = new PageImpl<>(List.of(mockResponse));

        when(routingRuleService.searchRoutingRules(categoryId, "Thành phố Hồ Chí Minh", "Phường Long Trường", departmentId, pageable))
                .thenReturn(mockPage);

        ResponseEntity<BaseResponse<Page<RoutingRuleResponse>>> response =
                routingRuleController.searchRoutingRules(categoryId, "Thành phố Hồ Chí Minh", "Phường Long Trường", departmentId, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("deleteRoutingRule_validId_returns200OK")
    void deleteRoutingRule_validId_returns200OK() {
        doNothing().when(routingRuleService).deleteRoutingRule(ruleId);

        ResponseEntity<BaseResponse<Void>> response = routingRuleController.deleteRoutingRule(ruleId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(routingRuleService, times(1)).deleteRoutingRule(ruleId);
    }
}
