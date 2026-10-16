package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.DepartmentMapper;
import com.cityfix.backend.model.dto.request.AssignDepartmentUsersRequest;
import com.cityfix.backend.model.dto.request.CreateDepartmentRequest;
import com.cityfix.backend.model.dto.request.UpdateDepartmentRequest;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.dto.response.DepartmentUserResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.UserDepartment;
import com.cityfix.backend.model.enums.DepartmentLevel;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.repository.UserDepartmentRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.impl.DepartmentServiceImpl;
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
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDepartmentRepository userDepartmentRepository;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

    private UUID departmentId;
    private Department mockDepartment;
    private DepartmentResponse mockResponse;
    private CreateDepartmentRequest createRequest;
    private UpdateDepartmentRequest updateRequest;

    @BeforeEach
    void setUp() {
        departmentId = UUID.randomUUID();

        mockDepartment = Department.builder()
                .departmentId(departmentId)
                .name("Phòng xử lý sự cố hạ tầng phường Long Trường")
                .level(DepartmentLevel.WARD)
                .contactEmail("longtruong@cityfix.vn")
                .phoneNumber("0901234567")
                .build();

        mockResponse = new DepartmentResponse(
                departmentId,
                "Phòng xử lý sự cố hạ tầng phường Long Trường",
                DepartmentLevel.WARD,
                "longtruong@cityfix.vn",
                "0901234567",
                0,
                true
        );

        createRequest = new CreateDepartmentRequest(
                "Phòng xử lý sự cố hạ tầng phường Long Trường",
                DepartmentLevel.WARD,
                "longtruong@cityfix.vn",
                "0901234567"
        );

        updateRequest = new UpdateDepartmentRequest(
                "Phòng QLĐT Quận 9",
                DepartmentLevel.CITY,
                "quan9@cityfix.vn",
                "0909999999",
                true
        );
    }

    @Test
    @DisplayName("createDepartment_validData_returnsDepartmentResponse")
    void createDepartment_validData_returnsDepartmentResponse() {
        when(departmentRepository.existsByNameAndIsDeletedFalse(createRequest.name())).thenReturn(false);
        when(departmentMapper.toEntity(createRequest)).thenReturn(mockDepartment);
        when(departmentRepository.save(any(Department.class))).thenReturn(mockDepartment);
        when(departmentMapper.toResponse(mockDepartment)).thenReturn(mockResponse);

        DepartmentResponse result = departmentService.createDepartment(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(createRequest.name());
        assertThat(result.level()).isEqualTo(DepartmentLevel.WARD);

        verify(departmentRepository, times(1)).existsByNameAndIsDeletedFalse(createRequest.name());
        verify(departmentRepository, times(1)).save(any(Department.class));
    }

    @Test
    @DisplayName("createDepartment_duplicateName_throwsDepartmentNameExistsException")
    void createDepartment_duplicateName_throwsDepartmentNameExistsException() {
        when(departmentRepository.existsByNameAndIsDeletedFalse(createRequest.name())).thenReturn(true);

        assertThatThrownBy(() -> departmentService.createDepartment(createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NAME_EXISTS);

        verify(departmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateDepartment_validData_returnsUpdatedDepartmentResponse")
    void updateDepartment_validData_returnsUpdatedDepartmentResponse() {
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(departmentRepository.existsByNameAndDepartmentIdNotAndIsDeletedFalse(updateRequest.name(), departmentId)).thenReturn(false);
        when(departmentRepository.save(mockDepartment)).thenReturn(mockDepartment);
        when(departmentMapper.toResponse(mockDepartment)).thenReturn(mockResponse);

        DepartmentResponse result = departmentService.updateDepartment(departmentId, updateRequest);

        assertThat(result).isNotNull();
        verify(departmentMapper, times(1)).updateEntityFromRequest(updateRequest, mockDepartment);
        verify(departmentRepository, times(1)).save(mockDepartment);
    }

    @Test
    @DisplayName("updateDepartment_departmentNotFound_throwsDepartmentNotFoundException")
    void updateDepartment_departmentNotFound_throwsDepartmentNotFoundException() {
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.updateDepartment(departmentId, updateRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);

        verify(departmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("getDepartmentById_validId_returnsDepartmentResponse")
    void getDepartmentById_validId_returnsDepartmentResponse() {
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(departmentMapper.toResponse(mockDepartment)).thenReturn(mockResponse);

        DepartmentResponse result = departmentService.getDepartmentById(departmentId);

        assertThat(result).isNotNull();
        assertThat(result.departmentId()).isEqualTo(departmentId);

        verify(departmentRepository, times(1)).findByDepartmentIdAndIsDeletedFalse(departmentId);
    }

    @Test
    @DisplayName("getAllDepartments_returnsPage")
    void getAllDepartments_returnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Department> page = new PageImpl<>(List.of(mockDepartment));

        when(departmentRepository.findAll(pageable)).thenReturn(page);
        when(departmentMapper.toResponse(mockDepartment)).thenReturn(mockResponse);

        Page<DepartmentResponse> result = departmentService.getAllDepartments(pageable, false);

        assertThat(result).hasSize(1);
        verify(departmentRepository, times(1)).findAll(pageable);
    }

    @Test
    @DisplayName("getAllDepartments_activeOnlyTrue_callsFindByIsDeletedFalse")
    void getAllDepartments_activeOnlyTrue_callsFindByIsDeletedFalse() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Department> page = new PageImpl<>(List.of(mockDepartment));

        when(departmentRepository.findByIsDeletedFalse(pageable)).thenReturn(page);
        when(departmentMapper.toResponse(mockDepartment)).thenReturn(mockResponse);

        Page<DepartmentResponse> result = departmentService.getAllDepartments(pageable, true);

        assertThat(result).hasSize(1);
        verify(departmentRepository, times(1)).findByIsDeletedFalse(pageable);
        verify(departmentRepository, never()).findAll(pageable);
    }

    @Test
    @DisplayName("deleteDepartment_validId_unlinksOfficersAndSoftDeletes")
    void deleteDepartment_validId_unlinksOfficersAndSoftDeletes() {
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));

        departmentService.deleteDepartment(departmentId);

        verify(departmentRepository, times(1)).save(mockDepartment);
        assertThat(mockDepartment.isDeleted()).isTrue();
        assertThat(mockDepartment.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("createDepartment_withManager_success")
    void createDepartment_withManager_success() {
        UUID managerId = UUID.randomUUID();
        User manager = new User();
        manager.setUserId(managerId);

        CreateDepartmentRequest req = new CreateDepartmentRequest(
                "Phòng Mới", DepartmentLevel.CITY, "moi@cityfix.vn", "0912345678", managerId
        );
        Department dept = new Department();

        when(departmentRepository.existsByNameAndIsDeletedFalse("Phòng Mới")).thenReturn(false);
        when(departmentMapper.toEntity(req)).thenReturn(dept);
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(departmentMapper.toResponse(dept)).thenReturn(mockResponse);

        DepartmentResponse res = departmentService.createDepartment(req);

        assertThat(res).isNotNull();
        assertThat(dept.getManager()).isEqualTo(manager);
        verify(userRepository).findById(managerId);
    }

    @Test
    @DisplayName("createDepartment_withInvalidManager_throwsNotFound")
    void createDepartment_withInvalidManager_throwsNotFound() {
        UUID managerId = UUID.randomUUID();
        CreateDepartmentRequest req = new CreateDepartmentRequest(
                "Phòng Mới", DepartmentLevel.CITY, "moi@cityfix.vn", "0912345678", managerId
        );

        when(departmentRepository.existsByNameAndIsDeletedFalse("Phòng Mới")).thenReturn(false);
        when(userRepository.findById(managerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.createDepartment(req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("updateDepartment_withManager_success")
    void updateDepartment_withManager_success() {
        UUID managerId = UUID.randomUUID();
        User manager = new User();
        manager.setUserId(managerId);

        UpdateDepartmentRequest req = new UpdateDepartmentRequest(
                "Phòng Sửa", DepartmentLevel.CITY, "sua@cityfix.vn", "0912345679", true, managerId
        );

        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(departmentRepository.existsByNameAndDepartmentIdNotAndIsDeletedFalse("Phòng Sửa", departmentId)).thenReturn(false);
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(departmentRepository.save(mockDepartment)).thenReturn(mockDepartment);
        when(departmentMapper.toResponse(mockDepartment)).thenReturn(mockResponse);

        DepartmentResponse res = departmentService.updateDepartment(departmentId, req);

        assertThat(res).isNotNull();
        assertThat(mockDepartment.getManager()).isEqualTo(manager);
        verify(userRepository).findById(managerId);
    }

    @Test
    @DisplayName("updateDepartment_withInvalidManager_throwsNotFound")
    void updateDepartment_withInvalidManager_throwsNotFound() {
        UUID managerId = UUID.randomUUID();
        UpdateDepartmentRequest req = new UpdateDepartmentRequest(
                "Phòng Sửa", DepartmentLevel.CITY, "sua@cityfix.vn", "0912345679", true, managerId
        );

        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(departmentRepository.existsByNameAndDepartmentIdNotAndIsDeletedFalse("Phòng Sửa", departmentId)).thenReturn(false);
        when(userRepository.findById(managerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.updateDepartment(departmentId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("assignUsersToDepartment_validRequest_success")
    void assignUsersToDepartment_validRequest_success() {
        UUID staffId = UUID.randomUUID();
        Role staffRole = new Role(UUID.randomUUID(), "STAFF");
        User staff = User.builder()
                .userId(staffId)
                .email("staff@cityfix.vn")
                .fullName("Staff User")
                .phoneNumber("0901234567")
                .role(staffRole)
                .status(UserStatus.ACTIVE)
                .build();

        AssignDepartmentUsersRequest req = new AssignDepartmentUsersRequest(List.of(staffId));

        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userDepartmentRepository.existsByUserUserIdAndDepartmentDepartmentId(staffId, departmentId)).thenReturn(false);

        UserDepartment ud = UserDepartment.builder()
                .user(staff)
                .department(mockDepartment)
                .createdAt(LocalDateTime.now())
                .build();
        when(userDepartmentRepository.findDepartmentUsersWithRole(departmentId)).thenReturn(List.of(ud));

        List<DepartmentUserResponse> result = departmentService.assignUsersToDepartment(departmentId, req);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(staffId);
        assertThat(result.get(0).getRoleName()).isEqualTo("STAFF");
        verify(userDepartmentRepository).save(any(UserDepartment.class));
        verify(userRepository).save(staff);
    }

    @Test
    @DisplayName("assignUsersToDepartment_departmentNotFound_throwsException")
    void assignUsersToDepartment_departmentNotFound_throwsException() {
        AssignDepartmentUsersRequest req = new AssignDepartmentUsersRequest(List.of(UUID.randomUUID()));
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.assignUsersToDepartment(departmentId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("assignUsersToDepartment_userNotStaffOrManager_throwsException")
    void assignUsersToDepartment_userNotStaffOrManager_throwsException() {
        UUID residentId = UUID.randomUUID();
        Role residentRole = new Role(UUID.randomUUID(), "RESIDENT");
        User resident = User.builder()
                .userId(residentId)
                .role(residentRole)
                .build();

        AssignDepartmentUsersRequest req = new AssignDepartmentUsersRequest(List.of(residentId));

        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(userRepository.findById(residentId)).thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> departmentService.assignUsersToDepartment(departmentId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("removeUserFromDepartment_success")
    void removeUserFromDepartment_success() {
        UUID staffId = UUID.randomUUID();
        User staff = User.builder()
                .userId(staffId)
                .department(mockDepartment)
                .build();
        mockDepartment.setManager(staff);

        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userDepartmentRepository.findDepartmentsByUserId(staffId)).thenReturn(List.of());

        departmentService.removeUserFromDepartment(departmentId, staffId);

        verify(userDepartmentRepository).deleteByUserUserIdAndDepartmentDepartmentId(staffId, departmentId);
        verify(userRepository).save(staff);
        verify(departmentRepository).save(mockDepartment);
        assertThat(staff.getDepartment()).isNull();
        assertThat(mockDepartment.getManager()).isNull();
    }

    @Test
    @DisplayName("getDepartmentUsers_success")
    void getDepartmentUsers_success() {
        UUID staffId = UUID.randomUUID();
        Role staffRole = new Role(UUID.randomUUID(), "STAFF");
        User staff = User.builder()
                .userId(staffId)
                .email("staff@cityfix.vn")
                .fullName("Staff User")
                .role(staffRole)
                .status(UserStatus.ACTIVE)
                .build();

        UserDepartment ud = UserDepartment.builder()
                .user(staff)
                .department(mockDepartment)
                .createdAt(LocalDateTime.now())
                .build();

        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(userDepartmentRepository.findDepartmentUsersWithRole(departmentId)).thenReturn(List.of(ud));

        List<DepartmentUserResponse> result = departmentService.getDepartmentUsers(departmentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("staff@cityfix.vn");
    }
}
