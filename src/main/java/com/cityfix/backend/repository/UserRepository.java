package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.UserStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress"})
    User findUserByEmailAndIsDeletedFalse(String email);

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress", "userDepartments", "userDepartments.department"})
    Optional<User> findByEmailAndIsDeletedFalse(String email);

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress", "userDepartments", "userDepartments.department"})
    Optional<User> findByUserIdAndIsDeletedFalse(UUID id);

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress"})
    List<User> findAllByIsDeletedFalse();

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress"})
    @Query("SELECT u FROM User u JOIN u.userProfile up WHERE up.phoneNumber = :phoneNumber")
    Optional<User> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress"})
    @Query("SELECT u FROM User u JOIN u.userProfile up WHERE up.phoneNumber = :phoneNumber AND u.isDeleted = false")
    Optional<User> findByPhoneNumberAndIsDeletedFalse(@Param("phoneNumber") String phoneNumber);

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress"})
    @Query("""
        SELECT u FROM User u
        WHERE u.isDeleted = false
          AND EXISTS (
              SELECT 1 FROM UserDepartment ud
              WHERE ud.user = u
                AND ud.department.departmentId = :departmentId
                AND ud.department.isDeleted = false
          )
        ORDER BY u.createdAt ASC, u.userId ASC
        LIMIT 1
        """)
    Optional<User> findFirstByDepartmentDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress"})
    @Query("""
        SELECT u FROM User u
        JOIN u.userProfile up
        JOIN up.role r
        WHERE UPPER(r.roleName) = 'STAFF'
          AND u.status = :status
          AND u.isDeleted = false
          AND EXISTS (
              SELECT 1 FROM UserDepartment ud
              WHERE ud.user = u
                AND ud.department.departmentId = :departmentId
                AND ud.department.isDeleted = false
          )
        ORDER BY u.createdAt ASC, u.userId ASC
        LIMIT 1
        """)
    Optional<User> findRepresentativeStaffByDepartmentIdAndStatus(@Param("departmentId") UUID departmentId, @Param("status") UserStatus status);

    default Optional<User> findRepresentativeStaffByDepartmentId(UUID departmentId) {
        return findRepresentativeStaffByDepartmentIdAndStatus(departmentId, UserStatus.ACTIVE);
    }

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress"})
    @Query("""
        SELECT u FROM User u
        JOIN u.userProfile up
        JOIN up.role r
        WHERE UPPER(r.roleName) = 'STAFF'
          AND u.status = :status
          AND u.isDeleted = false
          AND EXISTS (
              SELECT 1 FROM UserDepartment ud
              WHERE ud.user = u
                AND ud.department.departmentId = :departmentId
                AND ud.department.isDeleted = false
          )
        ORDER BY u.createdAt ASC, u.userId ASC
        """)
    List<User> findStaffByDepartmentIdAndStatus(@Param("departmentId") UUID departmentId, @Param("status") UserStatus status);

    default List<User> findActiveStaffByDepartmentId(UUID departmentId) {
        return findStaffByDepartmentIdAndStatus(departmentId, UserStatus.ACTIVE);
    }

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress"})
    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.userProfile up
        JOIN up.role r
        WHERE UPPER(r.roleName) = 'MANAGER'
          AND u.status = :status
          AND u.isDeleted = false
          AND EXISTS (
              SELECT 1 FROM UserDepartment ud
              WHERE ud.user = u
                AND ud.department.departmentId IN :departmentIds
                AND ud.department.isDeleted = false
          )
        """)
    List<User> findManagersByDepartmentIdsAndStatus(@Param("departmentIds") Collection<UUID> departmentIds, @Param("status") UserStatus status);

    default List<User> findActiveManagersByDepartmentIds(Collection<UUID> departmentIds) {
        return findManagersByDepartmentIdsAndStatus(departmentIds, UserStatus.ACTIVE);
    }

    @EntityGraph(attributePaths = {"userProfile", "userProfile.role", "userAddress"})
    @Query("SELECT DISTINCT u FROM User u JOIN u.userProfile up JOIN up.role r WHERE UPPER(r.roleName) = 'MANAGER' AND u.status = :status AND u.isDeleted = false")
    List<User> findManagersByStatus(@Param("status") UserStatus status);

    default List<User> findActiveManagers() {
        return findManagersByStatus(UserStatus.ACTIVE);
    }

    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN FETCH u.userProfile up
        JOIN FETCH up.role r
        JOIN FETCH u.userAddress ua
        WHERE UPPER(r.roleName) = 'RESIDENT'
          AND u.status = :status
          AND u.isDeleted = false
          AND ua.ward IS NOT NULL
          AND TRIM(ua.ward) <> ''
        """)
    List<User> findResidentsWithWardByStatus(@Param("status") UserStatus status);

    default List<User> findActiveResidentsWithWard() {
        return findResidentsWithWardByStatus(UserStatus.ACTIVE);
    }

    @Query("SELECT u.status, COUNT(u) FROM User u WHERE u.isDeleted = false GROUP BY u.status")
    List<Object[]> countUsersGroupByStatus();

    @Query("SELECT UPPER(r.roleName), COUNT(u) FROM User u JOIN u.userProfile up JOIN up.role r WHERE u.isDeleted = false GROUP BY UPPER(r.roleName)")
    List<Object[]> countUsersGroupByRole();
}
