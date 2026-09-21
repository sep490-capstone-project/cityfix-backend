package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.AdminUserRequest;
import com.cityfix.backend.model.dto.response.AdminUserResponse;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private AdminUserService adminUserService;

    @InjectMocks
    private AdminUserController adminUserController;

    private UUID userId;
    private AdminUserRequest validRequest;
    private AdminUserResponse mockResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        validRequest = AdminUserRequest.builder()
                .roleId(UUID.randomUUID())
                .email("test@example.com")
                .phoneNumber("0901234567")
                .password("password123")
                .fullName("Test User")
                .province("Province")
                .ward("Ward")
                .address("Address")
                .status(UserStatus.ACTIVE)
                .build();

        mockResponse = AdminUserResponse.builder()
                .userId(userId)
                .email("test@example.com")
                .fullName("Test User")
                .build();
    }

    @Test
    @DisplayName("getAllUsers_returns200OK")
    void getAllUsers_returns200OK() {
        when(adminUserService.getAllUsers()).thenReturn(List.of(mockResponse));

        ResponseEntity<BaseResponse<List<AdminUserResponse>>> response = adminUserController.getAllUsers();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("createUser_validRequest_returns200OK")
    void createUser_validRequest_returns200OK() {
        when(adminUserService.createUser(any(AdminUserRequest.class))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<AdminUserResponse>> response = adminUserController.createUser(validRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(adminUserService, times(1)).createUser(validRequest);
    }

    @Test
    @DisplayName("updateUser_validRequest_returns200OK")
    void updateUser_validRequest_returns200OK() {
        when(adminUserService.updateUser(eq(userId), any(AdminUserRequest.class))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<AdminUserResponse>> response = adminUserController.updateUser(userId, validRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(adminUserService, times(1)).updateUser(userId, validRequest);
    }

    @Test
    @DisplayName("deleteUser_validId_returns200OK")
    void deleteUser_validId_returns200OK() {
        doNothing().when(adminUserService).deleteUser(userId);

        ResponseEntity<BaseResponse<Void>> response = adminUserController.deleteUser(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(adminUserService, times(1)).deleteUser(userId);
    }
}
