package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    User findUserByEmailAndIsDeletedFalse(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndIsDeletedFalse(String email);
    Optional<User> findByUserIdAndIsDeletedFalse(UUID id);
    List<User> findAllByIsDeletedFalse();
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByPhoneNumberAndIsDeletedFalse(String phoneNumber);
    Optional<User> findFirstByDepartmentDepartmentIdAndIsDeletedFalse(UUID departmentId);

    @Query("SELECT u FROM User u WHERE u.department.departmentId = :departmentId AND UPPER(u.role.roleName) = 'STAFF' AND u.status = com.cityfix.backend.model.enums.UserStatus.ACTIVE AND u.isDeleted = false ORDER BY u.createdAt ASC, u.userId ASC")
    Optional<User> findRepresentativeStaffByDepartmentId(@Param("departmentId") UUID departmentId);

    @Query("SELECT u FROM User u WHERE u.department.departmentId = :departmentId AND UPPER(u.role.roleName) = 'STAFF' AND u.status = com.cityfix.backend.model.enums.UserStatus.ACTIVE AND u.isDeleted = false ORDER BY u.createdAt ASC, u.userId ASC")
    List<User> findActiveStaffByDepartmentId(@Param("departmentId") UUID departmentId);
}
