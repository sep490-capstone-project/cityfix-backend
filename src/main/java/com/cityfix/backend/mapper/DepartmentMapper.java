package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.request.CreateDepartmentRequest;
import com.cityfix.backend.model.dto.request.UpdateDepartmentRequest;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.entity.Department;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DepartmentMapper {

    @Mapping(target = "departmentId", ignore = true)
    @Mapping(target = "officers", ignore = true)
    @Mapping(target = "manager", ignore = true)
    Department toEntity(CreateDepartmentRequest request);

    @Mapping(target = "officerCount", expression = "java(department.getOfficers() != null ? department.getOfficers().size() : 0)")
    @Mapping(target = "isActive", expression = "java(!department.isDeleted())")
    @Mapping(target = "managerId", source = "manager.userId")
    @Mapping(target = "managerName", source = "manager.fullName")
    DepartmentResponse toResponse(Department department);

    List<DepartmentResponse> toResponseList(List<Department> departments);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "departmentId", ignore = true)
    @Mapping(target = "officers", ignore = true)
    @Mapping(target = "manager", ignore = true)
    void updateEntityFromRequest(UpdateDepartmentRequest request, @MappingTarget Department department);
}
