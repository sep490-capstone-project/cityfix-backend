package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.SubCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubCategoryRepository extends JpaRepository<SubCategory, UUID> {
    List<SubCategory> findByIsActiveTrue();
    List<SubCategory> findByCategory_CategoryIdAndIsActiveTrue(UUID categoryId);
}
