package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.SubCategoryRequest;
import com.cityfix.backend.model.dto.response.SubCategoryResponse;

import java.util.List;
import java.util.UUID;

public interface SubCategoryService {
    SubCategoryResponse createSubCategory(SubCategoryRequest request);
    SubCategoryResponse updateSubCategory(UUID id, SubCategoryRequest request);
    void deleteSubCategory(UUID id);
    SubCategoryResponse getSubCategory(UUID id);
    List<SubCategoryResponse> getAllSubCategories();
    List<SubCategoryResponse> getSubCategoriesByCategoryId(UUID categoryId);
}
