package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.UserDepartment;
import com.cityfix.backend.model.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserDepartmentRepository extends JpaRepository<UserDepartment, UUID> {

    List<UserDepartment> findByUserUserId(UUID userId);

    List<UserDepartment> findByDepartmentDepartmentId(UUID departmentId);

    boolean existsByUserUserIdAndDepartmentDepartmentId(UUID userId, UUID departmentId);

    @Query("SELECT ud.department FROM UserDepartment ud WHERE ud.user.userId = :userId AND ud.department.isDeleted = false")
    List<Department> findDepartmentsByUserId(@Param("userId") UUID userId);

    @Query("SELECT ud.user FROM UserDepartment ud WHERE ud.department.departmentId = :departmentId AND UPPER(ud.user.role.roleName) = 'STAFF' AND ud.user.status = :status AND ud.user.isDeleted = false ORDER BY ud.user.createdAt ASC, ud.user.userId ASC")
    List<User> findStaffByDepartmentIdAndStatus(@Param("departmentId") UUID departmentId, @Param("status") UserStatus status);

    default List<User> findActiveStaffByDepartmentId(UUID departmentId) {
        return findStaffByDepartmentIdAndStatus(departmentId, UserStatus.ACTIVE);
    }

    @Query("SELECT DISTINCT ud.user.userId FROM UserDepartment ud WHERE ud.user.userId IN :userIds")
    Set<UUID> findUserIdsWithDepartment(@Param("userIds") Collection<UUID> userIds);
}
