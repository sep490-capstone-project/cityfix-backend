package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.model.dto.request.AdminUserRequest;
import com.cityfix.backend.model.dto.response.AdminUserResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.repository.RoleRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private DepartmentRepository departmentRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private UUID userId;
    private UUID roleId;
    private AdminUserRequest userRequest;
    private User user;
    private Role role;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        roleId = UUID.randomUUID();

        role = new Role(roleId, "RESIDENT");

        userRequest = AdminUserRequest.builder()
                .roleId(roleId)
                .email("test@example.com")
                .phoneNumber("0901234567")
                .password("password123")
                .fullName("Test User")
                .province("Province")
                .ward("Ward")
                .address("Address")
                .status(UserStatus.ACTIVE)
                .build();

        user = User.builder()
                .userId(userId)
                .email("test@example.com")
                .phoneNumber("0901234567")
                .passwordHash("hashedpassword")
                .fullName("Test User")
                .province("Province")
                .ward("Ward")
                .address("Address")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("getAllUsers_returnsList")
    void getAllUsers_returnsList() {
        when(userRepository.findAllByIsDeletedFalse()).thenReturn(List.of(user));

        List<AdminUserResponse> result = adminUserService.getAllUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("createUser_validRequest_returnsResponse")
    void createUser_validRequest_returnsResponse() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRepository.findByEmailAndIsDeletedFalse(userRequest.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumberAndIsDeletedFalse(userRequest.getPhoneNumber())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(userRequest.getPassword())).thenReturn("hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        AdminUserResponse result = adminUserService.createUser(userRequest);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("createUser_adminRole_throwsException")
    void createUser_adminRole_throwsException() {
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN");
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(adminRole));

        assertThatThrownBy(() -> adminUserService.createUser(userRequest))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Cannot create user with ADMIN role");
    }

    @Test
    @DisplayName("updateUser_validRequest_returnsResponse")
    void updateUser_validRequest_returnsResponse() {
        when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRepository.findByEmailAndIsDeletedFalse(userRequest.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumberAndIsDeletedFalse(userRequest.getPhoneNumber())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(userRequest.getPassword())).thenReturn("newhashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        AdminUserResponse result = adminUserService.updateUser(userId, userRequest);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("updateUser_adminRoleChange_throwsException")
    void updateUser_adminRoleChange_throwsException() {
        when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN");
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(adminRole));

        assertThatThrownBy(() -> adminUserService.updateUser(userId, userRequest))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Cannot change role to ADMIN");
    }

    @Test
    @DisplayName("deleteUser_validId_setsDeletedTrue")
    void deleteUser_validId_setsDeletedTrue() {
        when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        adminUserService.deleteUser(userId);

        assertThat(user.isDeleted()).isTrue();
        verify(userRepository, times(1)).save(user);
    }
}
