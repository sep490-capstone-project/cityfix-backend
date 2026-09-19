package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIsDeletedFalse(String name);

    boolean existsByNameAndDepartmentIdNot(String name, UUID departmentId);

    boolean existsByNameAndDepartmentIdNotAndIsDeletedFalse(String name, UUID departmentId);

    Optional<Department> findByDepartmentIdAndIsDeletedFalse(UUID departmentId);

    Page<Department> findByIsDeletedFalse(Pageable pageable);
}
