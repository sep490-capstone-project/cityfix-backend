package com.cityfix.backend.mapper;

import com.cityfix.backend.enums.AccountStatus;
import com.cityfix.backend.model.dto.request.UserRegisterRequest;
import com.cityfix.backend.model.dto.response.UserRegisterResponse;
import com.cityfix.backend.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "isDispatcher", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    User toUser(UserRegisterRequest request);

    @Mapping(source = ".", target = "status", qualifiedByName = "toAccountStatus")
    @Mapping(source = "userId", target = "id")
    @Mapping(source = "role.roleName", target = "role")
    @Mapping(target = "nextStep", ignore = true)
    UserRegisterResponse toCustomerRegisterResponse(User user);

    @Named("toAccountStatus")
    default AccountStatus toAccountStatus(User user) {
        if (user == null) return AccountStatus.INACTIVE;
        return user.isEmailVerified() ? AccountStatus.ACTIVE : AccountStatus.INACTIVE;
    }
}
