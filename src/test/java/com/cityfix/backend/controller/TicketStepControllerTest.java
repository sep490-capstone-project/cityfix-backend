package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.service.TicketStepService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketStepControllerTest {

    @Mock
    private TicketStepService ticketStepService;

    @InjectMocks
    private TicketStepController ticketStepController;

    @Test
    @DisplayName("updateTicketStepStatus_validRequest_returnsUpdatedStep")
    void updateTicketStepStatus_validRequest_returnsUpdatedStep() {
        UUID stepId = UUID.randomUUID();
        AssignmentStatus status = AssignmentStatus.IN_PROGRESS;

        TicketStepResponse mockResponse = TicketStepResponse.builder()
                .id(stepId)
                .status(status)
                .build();

        when(ticketStepService.updateTicketStepStatus(eq(stepId), eq(status))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<TicketStepResponse>> response =
                ticketStepController.updateTicketStepStatus(stepId, status);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("getTicketStepsByTicketId_validRequest_returnsSteps")
    void getTicketStepsByTicketId_validRequest_returnsSteps() {
        UUID ticketId = UUID.randomUUID();
        TicketStepResponse mockResponse = TicketStepResponse.builder().id(UUID.randomUUID()).build();

        when(ticketStepService.getTicketStepsByTicketId(ticketId)).thenReturn(java.util.List.of(mockResponse));

        ResponseEntity<BaseResponse<java.util.List<TicketStepResponse>>> response =
                ticketStepController.getTicketStepsByTicketId(ticketId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("deleteTicketStep_validRequest_returnsSuccess")
    void deleteTicketStep_validRequest_returnsSuccess() {
        UUID stepId = UUID.randomUUID();

        ResponseEntity<BaseResponse<Void>> response = ticketStepController.deleteTicketStep(stepId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        org.mockito.Mockito.verify(ticketStepService).deleteTicketStep(stepId);
    }

    @Test
    @DisplayName("updateTicketStepStatus_securityAnnotation_strictlyRestrictedToStaff")
    void updateTicketStepStatus_securityAnnotation_strictlyRestrictedToStaff() throws NoSuchMethodException {
        java.lang.reflect.Method method = TicketStepController.class.getMethod(
                "updateTicketStepStatus", UUID.class, AssignmentStatus.class
        );
        org.springframework.security.access.prepost.PreAuthorize preAuthorize =
                method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('STAFF')");
    }

    @Test
    @DisplayName("deleteTicketStep_securityAnnotation_strictlyRestrictedToManager")
    void deleteTicketStep_securityAnnotation_strictlyRestrictedToManager() throws NoSuchMethodException {
        java.lang.reflect.Method method = TicketStepController.class.getMethod(
                "deleteTicketStep", UUID.class
        );
        org.springframework.security.access.prepost.PreAuthorize preAuthorize =
                method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('MANAGER')");
    }

    @Test
    @DisplayName("getTicketStepsByTicketId_securityAnnotation_restrictedToStaffAndManager")
    void getTicketStepsByTicketId_securityAnnotation_restrictedToStaffAndManager() throws NoSuchMethodException {
        java.lang.reflect.Method method = TicketStepController.class.getMethod(
                "getTicketStepsByTicketId", UUID.class
        );
        org.springframework.security.access.prepost.PreAuthorize preAuthorize =
                method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('STAFF', 'MANAGER')");
    }
}
