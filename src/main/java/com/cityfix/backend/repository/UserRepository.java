package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
