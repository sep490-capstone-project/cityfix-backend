package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.AdminUserRequest;
import com.cityfix.backend.model.dto.response.AdminUserResponse;
import java.util.List;
import java.util.UUID;

public interface AdminUserService {
    List<AdminUserResponse> getAllUsers();
    AdminUserResponse createUser(AdminUserRequest request);
    AdminUserResponse updateUser(UUID id, AdminUserRequest request);
    void deleteUser(UUID id);
}
