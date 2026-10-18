package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.request.UserRegisterRequest;
import com.cityfix.backend.model.dto.response.UserRegisterResponse;
import com.cityfix.backend.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    User toUser(UserRegisterRequest request);

    @Mapping(source = "status", target = "status")
    @Mapping(source = "userId", target = "id")
    @Mapping(source = "role.roleName", target = "role")
    @Mapping(target = "nextStep", ignore = true)
    UserRegisterResponse toCustomerRegisterResponse(User user);

    @Mapping(source = "role.roleName", target = "roleName")
    @Mapping(source = "department.departmentId", target = "departmentId")
    com.cityfix.backend.model.dto.response.UserProfileResponse toUserProfileResponse(User user);

    @org.mapstruct.BeanMapping(nullValuePropertyMappingStrategy = org.mapstruct.NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromRequest(com.cityfix.backend.model.dto.request.UpdateProfileRequest request, @org.mapstruct.MappingTarget User user);
}
