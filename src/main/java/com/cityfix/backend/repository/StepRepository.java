package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.Step;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StepRepository extends JpaRepository<Step, UUID> {
    List<Step> findBySubCategory_SubCategoryId(UUID subCategoryId);
    List<Step> findBySubCategory_SubCategoryIdAndIsDeletedFalse(UUID subCategoryId);
    java.util.Optional<Step> findBySubCategory_SubCategoryIdAndStepNameAndIsDeletedFalse(UUID subCategoryId, String stepName);
}
