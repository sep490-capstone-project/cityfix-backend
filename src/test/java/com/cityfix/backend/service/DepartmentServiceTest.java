package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.DepartmentMapper;
import com.cityfix.backend.model.dto.request.AssignDepartmentUsersRequest;
import com.cityfix.backend.model.dto.request.CreateDepartmentRequest;
import com.cityfix.backend.model.dto.request.UpdateDepartmentRequest;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.dto.response.DepartmentUserResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.RoutingRule;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.UserDepartment;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.repository.RoutingRuleRepository;
import com.cityfix.backend.repository.SubCategoryRepository;
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

    @Mock
    private SubCategoryRepository subCategoryRepository;

    @Mock
    private RoutingRuleRepository routingRuleRepository;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

    private UUID departmentId;
    private Department mockDepartment;
    private DepartmentResponse mockResponse;
    private CreateDepartmentRequest createRequest;
    private UpdateDepartmentRequest updateRequest;

    @BeforeEach
    void setUp() {
        lenient().when(departmentMapper.toDepartmentUserResponse(any(UserDepartment.class)))
                .thenAnswer(inv -> {
                    UserDepartment ud = inv.getArgument(0);
                    return departmentMapper.getClass().getInterfaces()[0].equals(DepartmentMapper.class)
                            ? ud != null && ud.getUser() != null
                                ? DepartmentUserResponse.builder()
                                    .userId(ud.getUser().getUserId())
                                    .email(ud.getUser().getEmail())
                                    .fullName(ud.getUser().getFullName())
                                    .phoneNumber(ud.getUser().getPhoneNumber())
                                    .roleName(ud.getUser().getRole() != null ? ud.getUser().getRole().getRoleName() : null)
                                    .status(ud.getUser().getStatus())
                                    .joinedAt(ud.getCreatedAt())
                                    .build()
                                : null
                            : null;
                });

        departmentId = UUID.randomUUID();

        mockDepartment = Department.builder()
                .departmentId(departmentId)
                .name("Phòng xử lý sự cố hạ tầng phường Long Trường")
                .contactEmail("longtruong@cityfix.vn")
                .phoneNumber("0901234567")
                .build();

        mockResponse = new DepartmentResponse(
                departmentId,
                "Phòng xử lý sự cố hạ tầng phường Long Trường",
                "longtruong@cityfix.vn",
                "0901234567",
                0,
                true
        );

        createRequest = new CreateDepartmentRequest(
                "Phòng xử lý sự cố hạ tầng phường Long Trường",
                "longtruong@cityfix.vn",
                "0901234567"
        );

        updateRequest = new UpdateDepartmentRequest(
                "Phòng QLĐT Quận 9",
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
    @DisplayName("updateDepartment_duplicateName_throwsDepartmentNameExistsException")
    void updateDepartment_duplicateName_throwsDepartmentNameExistsException() {
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(departmentRepository.existsByNameAndDepartmentIdNotAndIsDeletedFalse(updateRequest.name(), departmentId)).thenReturn(true);

        assertThatThrownBy(() -> departmentService.updateDepartment(departmentId, updateRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NAME_EXISTS);

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
    @DisplayName("getDepartmentById_nonExistentOrSoftDeleted_throwsDepartmentNotFoundException")
    void getDepartmentById_nonExistentOrSoftDeleted_throwsDepartmentNotFoundException() {
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.getDepartmentById(departmentId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);

        verify(departmentMapper, never()).toResponse(any());
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
    @DisplayName("getAllDepartments_activeOnlyTrueWhenNoActiveDepartments_returnsEmptyPage")
    void getAllDepartments_activeOnlyTrueWhenNoActiveDepartments_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Department> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(departmentRepository.findByIsDeletedFalse(pageable)).thenReturn(emptyPage);

        Page<DepartmentResponse> result = departmentService.getAllDepartments(pageable, true);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        verify(departmentRepository, times(1)).findByIsDeletedFalse(pageable);
        verify(departmentMapper, never()).toResponse(any());
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
    @DisplayName("deleteDepartment_nonExistentId_throwsDepartmentNotFoundException")
    void deleteDepartment_nonExistentId_throwsDepartmentNotFoundException() {
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.deleteDepartment(departmentId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);

        verify(departmentRepository, never()).save(any());
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
    @DisplayName("assignUsersToDepartment_userNotFound_throwsAccountNotFoundException")
    void assignUsersToDepartment_userNotFound_throwsAccountNotFoundException() {
        UUID unknownUserId = UUID.randomUUID();
        AssignDepartmentUsersRequest req = new AssignDepartmentUsersRequest(List.of(unknownUserId));

        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(userRepository.findById(unknownUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.assignUsersToDepartment(departmentId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        verify(userDepartmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeUserFromDepartment_success")
    void removeUserFromDepartment_success() {
        UUID staffId = UUID.randomUUID();
        User staff = User.builder()
                .userId(staffId)
                .department(mockDepartment)
                .build();

        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));

        departmentService.removeUserFromDepartment(departmentId, staffId);

        verify(userDepartmentRepository).deleteByUserUserIdAndDepartmentDepartmentId(staffId, departmentId);
    }

    @Test
    @DisplayName("removeUserFromDepartment_userNotFound_throwsAccountNotFoundException")
    void removeUserFromDepartment_userNotFound_throwsAccountNotFoundException() {
        UUID unknownUserId = UUID.randomUUID();
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(userRepository.findById(unknownUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.removeUserFromDepartment(departmentId, unknownUserId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        verify(userDepartmentRepository, never()).deleteByUserUserIdAndDepartmentDepartmentId(any(), any());
    }

    @Test
    @DisplayName("removeUserFromDepartment_departmentNotFound_throwsDepartmentNotFoundException")
    void removeUserFromDepartment_departmentNotFound_throwsDepartmentNotFoundException() {
        UUID unknownDeptId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(unknownDeptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.removeUserFromDepartment(unknownDeptId, staffId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);

        verify(userDepartmentRepository, never()).deleteByUserUserIdAndDepartmentDepartmentId(any(), any());
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

    @Test
    @DisplayName("getDepartmentUsers_whenNoUsersAssigned_returnsEmptyList")
    void getDepartmentUsers_whenNoUsersAssigned_returnsEmptyList() {
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(mockDepartment));
        when(userDepartmentRepository.findDepartmentUsersWithRole(departmentId)).thenReturn(List.of());

        List<DepartmentUserResponse> result = departmentService.getDepartmentUsers(departmentId);

        assertThat(result).isNotNull().isEmpty();
        verify(userDepartmentRepository).findDepartmentUsersWithRole(departmentId);
    }

    @Test
    @DisplayName("getDepartmentUsers_departmentNotFound_throwsDepartmentNotFoundException")
    void getDepartmentUsers_departmentNotFound_throwsDepartmentNotFoundException() {
        UUID unknownDeptId = UUID.randomUUID();
        when(departmentRepository.findByDepartmentIdAndIsDeletedFalse(unknownDeptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.getDepartmentUsers(unknownDeptId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);

        verify(userDepartmentRepository, never()).findDepartmentUsersWithRole(any());
    }

    @Test
    @DisplayName("getDepartmentStaffBySubCategoryIds_normalizedLocationMatch_returnsActiveStaffOnly")
    void getDepartmentStaffBySubCategoryIds_normalizedLocationMatch_returnsActiveStaffOnly() {
        UUID subCatId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        Category category = Category.builder().categoryId(catId).name("Giao thông").isActive(true).build();
        SubCategory subCat = SubCategory.builder().subCategoryId(subCatId).name("Ổ gà").category(category).isActive(true).build();

        RoutingRule rule = RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(category)
                .department(mockDepartment)
                .province("Thành phố Hồ Chí Minh")
                .ward("Phường Đức Nhuận")
                .build();

        Role staffRole = new Role(UUID.randomUUID(), "STAFF");
        Role managerRole = new Role(UUID.randomUUID(), "MANAGER");

        User activeStaff = User.builder()
                .userId(UUID.randomUUID())
                .email("staff.active@cityfix.vn")
                .fullName("Cán bộ Đang hoạt động")
                .role(staffRole)
                .status(UserStatus.ACTIVE)
                .build();
        User blockedStaff = User.builder()
                .userId(UUID.randomUUID())
                .email("staff.blocked@cityfix.vn")
                .fullName("Cán bộ Bị khóa")
                .role(staffRole)
                .status(UserStatus.BLOCKED)
                .build();
        User managerUser = User.builder()
                .userId(UUID.randomUUID())
                .email("manager@cityfix.vn")
                .fullName("Quản lý")
                .role(managerRole)
                .status(UserStatus.ACTIVE)
                .build();

        UserDepartment ud1 = UserDepartment.builder().user(activeStaff).department(mockDepartment).createdAt(LocalDateTime.now()).build();
        UserDepartment ud2 = UserDepartment.builder().user(blockedStaff).department(mockDepartment).createdAt(LocalDateTime.now()).build();
        UserDepartment ud3 = UserDepartment.builder().user(managerUser).department(mockDepartment).createdAt(LocalDateTime.now()).build();

        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(Optional.of(subCat));
        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(catId))
                .thenReturn(List.of(rule));
        when(userDepartmentRepository.findDepartmentUsersWithRole(departmentId))
                .thenReturn(List.of(ud1, ud2, ud3));

        List<DepartmentUserResponse> result = departmentService.getDepartmentStaffBySubCategoryIds(
                List.of(subCatId), "TP. Hồ Chí Minh", "p. đức nhuận");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(activeStaff.getUserId());
        assertThat(result.get(0).getEmail()).isEqualTo("staff.active@cityfix.vn");
        assertThat(result.get(0).getRoleName()).isEqualTo("STAFF");
    }

    @Test
    @DisplayName("getDepartmentStaffBySubCategoryIds_multipleSubCategoriesAndDepartments_deduplicatesStaff")
    void getDepartmentStaffBySubCategoryIds_multipleSubCategoriesAndDepartments_deduplicatesStaff() {
        UUID subCatId1 = UUID.randomUUID();
        UUID subCatId2 = UUID.randomUUID();
        UUID catId1 = UUID.randomUUID();
        UUID catId2 = UUID.randomUUID();

        Category cat1 = Category.builder().categoryId(catId1).name("Giao thông").isActive(true).build();
        Category cat2 = Category.builder().categoryId(catId2).name("Chiếu sáng").isActive(true).build();
        SubCategory subCat1 = SubCategory.builder().subCategoryId(subCatId1).name("Ổ gà").category(cat1).isActive(true).build();
        SubCategory subCat2 = SubCategory.builder().subCategoryId(subCatId2).name("Đèn đường").category(cat2).isActive(true).build();

        UUID deptId2 = UUID.randomUUID();
        Department dept2 = Department.builder().departmentId(deptId2).name("Phòng Chiếu sáng").build();

        RoutingRule rule1 = RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(cat1)
                .department(mockDepartment)
                .province("Thành phố Hồ Chí Minh")
                .ward("Phường Đức Nhuận")
                .build();
        RoutingRule rule2 = RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(cat2)
                .department(dept2)
                .province("Thành phố Hồ Chí Minh")
                .ward("Phường Đức Nhuận")
                .build();

        Role staffRole = new Role(UUID.randomUUID(), "STAFF");
        User sharedStaff = User.builder()
                .userId(UUID.randomUUID())
                .email("shared.staff@cityfix.vn")
                .fullName("Cán bộ Liên Phòng")
                .role(staffRole)
                .status(UserStatus.ACTIVE)
                .build();
        User dept2OnlyStaff = User.builder()
                .userId(UUID.randomUUID())
                .email("dept2.staff@cityfix.vn")
                .fullName("Cán bộ Phòng 2")
                .role(staffRole)
                .status(UserStatus.ACTIVE)
                .build();

        UserDepartment udDept1Shared = UserDepartment.builder().user(sharedStaff).department(mockDepartment).createdAt(LocalDateTime.now()).build();
        UserDepartment udDept2Shared = UserDepartment.builder().user(sharedStaff).department(dept2).createdAt(LocalDateTime.now()).build();
        UserDepartment udDept2Only = UserDepartment.builder().user(dept2OnlyStaff).department(dept2).createdAt(LocalDateTime.now()).build();

        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId1)).thenReturn(Optional.of(subCat1));
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId2)).thenReturn(Optional.of(subCat2));
        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(catId1)).thenReturn(List.of(rule1));
        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(catId2)).thenReturn(List.of(rule2));
        when(userDepartmentRepository.findDepartmentUsersWithRole(departmentId)).thenReturn(List.of(udDept1Shared));
        when(userDepartmentRepository.findDepartmentUsersWithRole(deptId2)).thenReturn(List.of(udDept2Shared, udDept2Only));

        List<DepartmentUserResponse> result = departmentService.getDepartmentStaffBySubCategoryIds(
                List.of(subCatId1, subCatId2, subCatId1), "TP.HCM", "đức nhuận");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DepartmentUserResponse::getUserId)
                .containsExactly(sharedStaff.getUserId(), dept2OnlyStaff.getUserId());
    }

    @Test
    @DisplayName("getDepartmentStaffBySubCategoryIds_noProvinceAndNoWard_returnsAllDepartmentsStaffOfCategories")
    void getDepartmentStaffBySubCategoryIds_noProvinceAndNoWard_returnsAllDepartmentsStaffOfCategories() {
        UUID subCatId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        Category category = Category.builder().categoryId(catId).name("Giao thông").isActive(true).build();
        SubCategory subCat = SubCategory.builder().subCategoryId(subCatId).name("Ổ gà").category(category).isActive(true).build();

        UUID deptId2 = UUID.randomUUID();
        Department dept2 = Department.builder().departmentId(deptId2).name("Phòng Hà Nội").build();

        RoutingRule ruleHcm = RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(category)
                .department(mockDepartment)
                .province("Thành phố Hồ Chí Minh")
                .ward("Phường Bến Nghé")
                .build();
        RoutingRule ruleHn = RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(category)
                .department(dept2)
                .province("Thành phố Hà Nội")
                .ward("Phường Điện Biên")
                .build();

        Role staffRole = new Role(UUID.randomUUID(), "STAFF");
        User staff1 = User.builder().userId(UUID.randomUUID()).email("s1@cityfix.vn").role(staffRole).status(UserStatus.ACTIVE).build();
        User staff2 = User.builder().userId(UUID.randomUUID()).email("s2@cityfix.vn").role(staffRole).status(UserStatus.ACTIVE).build();

        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(Optional.of(subCat));
        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(catId)).thenReturn(List.of(ruleHcm, ruleHn));
        when(userDepartmentRepository.findDepartmentUsersWithRole(departmentId))
                .thenReturn(List.of(UserDepartment.builder().user(staff1).department(mockDepartment).build()));
        when(userDepartmentRepository.findDepartmentUsersWithRole(deptId2))
                .thenReturn(List.of(UserDepartment.builder().user(staff2).department(dept2).build()));

        List<DepartmentUserResponse> result = departmentService.getDepartmentStaffBySubCategoryIds(
                List.of(subCatId), null, "   ");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DepartmentUserResponse::getUserId)
                .containsExactly(staff1.getUserId(), staff2.getUserId());
    }

    @Test
    @DisplayName("getDepartmentStaffBySubCategoryIds_onlyProvince_returnsAllWardsInThatProvinceOnly")
    void getDepartmentStaffBySubCategoryIds_onlyProvince_returnsAllWardsInThatProvinceOnly() {
        UUID subCatId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        Category category = Category.builder().categoryId(catId).name("Giao thông").isActive(true).build();
        SubCategory subCat = SubCategory.builder().subCategoryId(subCatId).name("Ổ gà").category(category).isActive(true).build();

        UUID deptHcm2Id = UUID.randomUUID();
        Department deptHcm2 = Department.builder().departmentId(deptHcm2Id).name("Phòng Q3").build();
        UUID deptHnId = UUID.randomUUID();
        Department deptHn = Department.builder().departmentId(deptHnId).name("Phòng Hà Nội").build();

        RoutingRule ruleHcm1 = RoutingRule.builder()
                .ruleId(UUID.randomUUID()).category(category).department(mockDepartment)
                .province("Thành phố Hồ Chí Minh").ward("Phường Bến Nghé").build();
        RoutingRule ruleHcm2 = RoutingRule.builder()
                .ruleId(UUID.randomUUID()).category(category).department(deptHcm2)
                .province("TP. Hồ Chí Minh").ward("Phường Võ Thị Sáu").build();
        RoutingRule ruleHn = RoutingRule.builder()
                .ruleId(UUID.randomUUID()).category(category).department(deptHn)
                .province("Thành phố Hà Nội").ward("Phường Điện Biên").build();

        Role staffRole = new Role(UUID.randomUUID(), "STAFF");
        User staff1 = User.builder().userId(UUID.randomUUID()).email("s1@cityfix.vn").role(staffRole).status(UserStatus.ACTIVE).build();
        User staff2 = User.builder().userId(UUID.randomUUID()).email("s2@cityfix.vn").role(staffRole).status(UserStatus.ACTIVE).build();

        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(Optional.of(subCat));
        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(catId))
                .thenReturn(List.of(ruleHcm1, ruleHcm2, ruleHn));
        when(userDepartmentRepository.findDepartmentUsersWithRole(departmentId))
                .thenReturn(List.of(UserDepartment.builder().user(staff1).department(mockDepartment).build()));
        when(userDepartmentRepository.findDepartmentUsersWithRole(deptHcm2Id))
                .thenReturn(List.of(UserDepartment.builder().user(staff2).department(deptHcm2).build()));

        List<DepartmentUserResponse> result = departmentService.getDepartmentStaffBySubCategoryIds(
                List.of(subCatId), "TPHCM", null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DepartmentUserResponse::getUserId)
                .containsExactly(staff1.getUserId(), staff2.getUserId());
        verify(userDepartmentRepository, never()).findDepartmentUsersWithRole(deptHnId);
    }

    @Test
    @DisplayName("getDepartmentStaffBySubCategoryIds_onlyWard_returnsMatchingWardAcrossDifferentProvinces")
    void getDepartmentStaffBySubCategoryIds_onlyWard_returnsMatchingWardAcrossDifferentProvinces() {
        UUID subCatId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        Category category = Category.builder().categoryId(catId).name("Giao thông").isActive(true).build();
        SubCategory subCat = SubCategory.builder().subCategoryId(subCatId).name("Ổ gà").category(category).isActive(true).build();

        UUID deptOtherProvId = UUID.randomUUID();
        Department deptOtherProv = Department.builder().departmentId(deptOtherProvId).name("Phòng Tỉnh B - P1").build();
        UUID deptDifferentWardId = UUID.randomUUID();
        Department deptDifferentWard = Department.builder().departmentId(deptDifferentWardId).name("Phòng Phường 2").build();

        RoutingRule ruleProvA = RoutingRule.builder()
                .ruleId(UUID.randomUUID()).category(category).department(mockDepartment)
                .province("Thành phố Hồ Chí Minh").ward("Phường Tân Định").build();
        RoutingRule ruleProvB = RoutingRule.builder()
                .ruleId(UUID.randomUUID()).category(category).department(deptOtherProv)
                .province("Tỉnh Bình Dương").ward("P. Tân Định").build();
        RoutingRule ruleOtherWard = RoutingRule.builder()
                .ruleId(UUID.randomUUID()).category(category).department(deptDifferentWard)
                .province("Thành phố Hồ Chí Minh").ward("Phường Bến Nghé").build();

        Role staffRole = new Role(UUID.randomUUID(), "STAFF");
        User staff1 = User.builder().userId(UUID.randomUUID()).email("s1@cityfix.vn").role(staffRole).status(UserStatus.ACTIVE).build();
        User staff2 = User.builder().userId(UUID.randomUUID()).email("s2@cityfix.vn").role(staffRole).status(UserStatus.ACTIVE).build();

        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(Optional.of(subCat));
        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(catId))
                .thenReturn(List.of(ruleProvA, ruleProvB, ruleOtherWard));
        when(userDepartmentRepository.findDepartmentUsersWithRole(departmentId))
                .thenReturn(List.of(UserDepartment.builder().user(staff1).department(mockDepartment).build()));
        when(userDepartmentRepository.findDepartmentUsersWithRole(deptOtherProvId))
                .thenReturn(List.of(UserDepartment.builder().user(staff2).department(deptOtherProv).build()));

        List<DepartmentUserResponse> result = departmentService.getDepartmentStaffBySubCategoryIds(
                List.of(subCatId), "  ", "tân định");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DepartmentUserResponse::getUserId)
                .containsExactly(staff1.getUserId(), staff2.getUserId());
        verify(userDepartmentRepository, never()).findDepartmentUsersWithRole(deptDifferentWardId);
    }

    @Test
    @DisplayName("getDepartmentStaffBySubCategoryIds_noMatchingRule_returnsEmptyList")
    void getDepartmentStaffBySubCategoryIds_noMatchingRule_returnsEmptyList() {
        UUID subCatId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        Category category = Category.builder().categoryId(catId).name("Giao thông").isActive(true).build();
        SubCategory subCat = SubCategory.builder().subCategoryId(subCatId).name("Ổ gà").category(category).isActive(true).build();

        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(Optional.of(subCat));
        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(catId))
                .thenReturn(List.of());

        List<DepartmentUserResponse> result = departmentService.getDepartmentStaffBySubCategoryIds(
                List.of(subCatId), "Tỉnh Cà Mau", "Phường 1");

        assertThat(result).isNotNull().isEmpty();
        verify(userDepartmentRepository, never()).findDepartmentUsersWithRole(any());
    }

    @Test
    @DisplayName("getDepartmentStaffBySubCategoryIds_subCategoryNotFound_throwsSubCategoryNotFound")
    void getDepartmentStaffBySubCategoryIds_subCategoryNotFound_throwsSubCategoryNotFound() {
        UUID subCatId1 = UUID.randomUUID();
        UUID subCatId2 = UUID.randomUUID();
        Category category = Category.builder().categoryId(UUID.randomUUID()).name("Giao thông").isActive(true).build();
        SubCategory subCat1 = SubCategory.builder().subCategoryId(subCatId1).name("Ổ gà").category(category).isActive(true).build();

        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId1)).thenReturn(Optional.of(subCat1));
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.getDepartmentStaffBySubCategoryIds(List.of(subCatId1, subCatId2), "TP.HCM", "Phường 1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBCATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("getDepartmentStaffBySubCategoryIds_emptyOrNullSubCategoryIds_throwsInvalidRequest")
    void getDepartmentStaffBySubCategoryIds_emptyOrNullSubCategoryIds_throwsInvalidRequest() {
        assertThatThrownBy(() -> departmentService.getDepartmentStaffBySubCategoryIds(null, "TP.HCM", "Phường 1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        assertThatThrownBy(() -> departmentService.getDepartmentStaffBySubCategoryIds(List.of(), "TP.HCM", "Phường 1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
