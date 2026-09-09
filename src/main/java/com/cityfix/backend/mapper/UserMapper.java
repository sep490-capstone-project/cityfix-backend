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

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    User toUser(UserRegisterRequest request);

    @Mapping(source = ".", target = "status", qualifiedByName = "toAccountStatus")
    @Mapping(source = "role.name", target = "role")
    UserRegisterResponse toCustomerRegisterResponse(User user);

    @Named("toAccountStatus")
    default AccountStatus toAccountStatus(User user) {
        if (user == null) return AccountStatus.INACTIVE;
        return user.isEmailVerified() ? AccountStatus.ACTIVE : AccountStatus.INACTIVE;
    }
}
