package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.DepartmentMapper;
import com.cityfix.backend.model.dto.request.CreateDepartmentRequest;
import com.cityfix.backend.model.dto.request.UpdateDepartmentRequest;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.enums.DepartmentLevel;
import com.cityfix.backend.repository.DepartmentRepository;
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
}
