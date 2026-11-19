package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.request.CreateDepartmentRequest;
import com.cityfix.backend.model.dto.request.UpdateDepartmentRequest;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.UserDepartment;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DepartmentMapper {

    @Mapping(target = "departmentId", ignore = true)
    @Mapping(target = "userDepartments", ignore = true)
    Department toEntity(CreateDepartmentRequest request);

    @Mapping(target = "officerCount", expression = "java(countActiveOfficers(department))")
    @Mapping(target = "isActive", expression = "java(!department.isDeleted())")
    @Mapping(target = "managerId", ignore = true)
    @Mapping(target = "managerName", ignore = true)
    DepartmentResponse toResponse(Department department);

    default int countActiveOfficers(Department department) {
        if (department == null || department.getUserDepartments() == null) {
            return 0;
        }
        try {
            return (int) department.getUserDepartments().stream()
                    .filter(ud -> ud != null && ud.getUser() != null && !ud.getUser().isDeleted())
                    .count();
        } catch (Exception ignored) {
            return 0;
        }
    }

    List<DepartmentResponse> toResponseList(List<Department> departments);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "departmentId", ignore = true)
    @Mapping(target = "userDepartments", ignore = true)
    void updateEntityFromRequest(UpdateDepartmentRequest request, @MappingTarget Department department);
}
