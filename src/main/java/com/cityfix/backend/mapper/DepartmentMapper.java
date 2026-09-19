package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.request.CreateDepartmentRequest;
import com.cityfix.backend.model.dto.request.UpdateDepartmentRequest;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.entity.Department;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DepartmentMapper {

    Department toEntity(CreateDepartmentRequest request);

    @Mapping(target = "officerCount", expression = "java(department.getOfficers() != null ? department.getOfficers().size() : 0)")
    DepartmentResponse toResponse(Department department);

    List<DepartmentResponse> toResponseList(List<Department> departments);

    void updateEntityFromRequest(UpdateDepartmentRequest request, @MappingTarget Department department);
}
