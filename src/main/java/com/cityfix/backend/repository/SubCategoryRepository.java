package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.SubCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubCategoryRepository extends JpaRepository<SubCategory, UUID> {
    @EntityGraph(attributePaths = {"category"})
    List<SubCategory> findByIsActiveTrueAndIsDeletedFalse();
    
    @EntityGraph(attributePaths = {"category"})
    List<SubCategory> findByCategory_CategoryIdAndIsActiveTrueAndIsDeletedFalse(UUID categoryId);
    
    @EntityGraph(attributePaths = {"category"})
    List<SubCategory> findByIsDeletedFalse();
    
    @EntityGraph(attributePaths = {"category"})
    java.util.Optional<SubCategory> findBySubCategoryIdAndIsDeletedFalse(UUID id);
}
