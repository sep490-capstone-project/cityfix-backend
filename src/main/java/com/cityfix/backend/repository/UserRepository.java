package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.UserStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"role"})
    User findUserByEmailAndIsDeletedFalse(String email);

    @EntityGraph(attributePaths = {"role"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"role"})
    Optional<User> findByEmailAndIsDeletedFalse(String email);

    Optional<User> findByUserIdAndIsDeletedFalse(UUID id);
    List<User> findAllByIsDeletedFalse();
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByPhoneNumberAndIsDeletedFalse(String phoneNumber);
    Optional<User> findFirstByDepartmentDepartmentIdAndIsDeletedFalse(UUID departmentId);

    @Query("SELECT u FROM User u WHERE u.department.departmentId = :departmentId AND UPPER(u.role.roleName) = 'STAFF' AND u.status = :status AND u.isDeleted = false ORDER BY u.createdAt ASC, u.userId ASC")
    Optional<User> findRepresentativeStaffByDepartmentIdAndStatus(@Param("departmentId") UUID departmentId, @Param("status") UserStatus status);

    default Optional<User> findRepresentativeStaffByDepartmentId(UUID departmentId) {
        return findRepresentativeStaffByDepartmentIdAndStatus(departmentId, UserStatus.ACTIVE);
    }

    @Query("SELECT u FROM User u WHERE u.department.departmentId = :departmentId AND UPPER(u.role.roleName) = 'STAFF' AND u.status = :status AND u.isDeleted = false ORDER BY u.createdAt ASC, u.userId ASC")
    List<User> findStaffByDepartmentIdAndStatus(@Param("departmentId") UUID departmentId, @Param("status") UserStatus status);

    default List<User> findActiveStaffByDepartmentId(UUID departmentId) {
        return findStaffByDepartmentIdAndStatus(departmentId, UserStatus.ACTIVE);
    }
}
