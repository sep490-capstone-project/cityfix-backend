package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.SubCategoryMapper;
import com.cityfix.backend.model.dto.request.SubCategoryRequest;
import com.cityfix.backend.model.dto.response.SubCategoryResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.repository.CategoryRepository;
import com.cityfix.backend.repository.SubCategoryRepository;
import com.cityfix.backend.service.impl.SubCategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubCategoryServiceTest {

    @Mock
    private SubCategoryRepository subCategoryRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private com.cityfix.backend.repository.StepRepository stepRepository;

    @Mock
    private SubCategoryMapper subCategoryMapper;

    @InjectMocks
    private SubCategoryServiceImpl subCategoryService;

    private SubCategoryRequest request;
    private Category category;
    private SubCategory subCategory;
    private SubCategoryResponse response;

    @BeforeEach
    void setUp() {
        UUID categoryId = UUID.randomUUID();
        UUID subCategoryId = UUID.randomUUID();

        request = new SubCategoryRequest();
        request.setCategoryId(categoryId);
        request.setName("Test");
        request.setPriority(Priority.HIGH);
        request.setIsActive(true);

        category = Category.builder().categoryId(categoryId).name("Cat").build();

        subCategory = SubCategory.builder()
                .subCategoryId(subCategoryId)
                .category(category)
                .name("Test")
                .priority(Priority.HIGH)
                .isActive(true)
                .build();

        response = SubCategoryResponse.builder()
                .subCategoryId(subCategoryId)
                .categoryId(categoryId)
                .name("Test")
                .priority(Priority.HIGH)
                .isActive(true)
                .build();
    }

    @Test
    void createSubCategory_Success() {
        when(categoryRepository.findByCategoryIdAndIsDeletedFalse(request.getCategoryId())).thenReturn(Optional.of(category));
        when(subCategoryMapper.toEntity(request)).thenReturn(subCategory);
        when(subCategoryRepository.save(any(SubCategory.class))).thenReturn(subCategory);
        when(subCategoryMapper.toResponse(subCategory)).thenReturn(response);

        SubCategoryResponse result = subCategoryService.createSubCategory(request);

        assertNotNull(result);
        assertEquals("Test", result.getName());
        assertEquals(0, subCategory.getSla());
        verify(subCategoryRepository).save(any(SubCategory.class));
    }

    @Test
    void updateSubCategory_Success_CalculatesSlaFromSteps() {
        UUID subCatId = subCategory.getSubCategoryId();
        com.cityfix.backend.model.entity.Step s1 = com.cityfix.backend.model.entity.Step.builder().duration(12).build();
        com.cityfix.backend.model.entity.Step s2 = com.cityfix.backend.model.entity.Step.builder().duration(24).build();

        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(Optional.of(subCategory));
        when(categoryRepository.findByCategoryIdAndIsDeletedFalse(request.getCategoryId())).thenReturn(Optional.of(category));
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(java.util.List.of(s1, s2));
        when(subCategoryRepository.save(any(SubCategory.class))).thenReturn(subCategory);
        when(subCategoryMapper.toResponse(subCategory)).thenReturn(response);

        SubCategoryResponse result = subCategoryService.updateSubCategory(subCatId, request);

        assertNotNull(result);
        assertEquals(36, subCategory.getSla());
        verify(subCategoryRepository).save(subCategory);
    }

    @Test
    void createSubCategory_CategoryNotFound() {
        when(categoryRepository.findByCategoryIdAndIsDeletedFalse(request.getCategoryId())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> subCategoryService.createSubCategory(request));
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getAllSubCategories_WhenExist_ReturnsList() {
        when(subCategoryRepository.findByIsDeletedFalse()).thenReturn(java.util.List.of(subCategory));
        when(subCategoryMapper.toResponse(subCategory)).thenReturn(response);

        java.util.List<SubCategoryResponse> result = subCategoryService.getAllSubCategories();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test", result.get(0).getName());
        verify(subCategoryRepository).findByIsDeletedFalse();
    }

    @Test
    void getAllSubCategories_WhenEmpty_ReturnsEmptyList() {
        when(subCategoryRepository.findByIsDeletedFalse()).thenReturn(java.util.Collections.emptyList());

        java.util.List<SubCategoryResponse> result = subCategoryService.getAllSubCategories();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(subCategoryRepository).findByIsDeletedFalse();
    }

    @Test
    void getSubCategoriesByCategoryId_WhenExist_ReturnsMatchingList() {
        UUID catId = category.getCategoryId();
        when(subCategoryRepository.findByCategory_CategoryIdAndIsActiveTrueAndIsDeletedFalse(catId))
                .thenReturn(java.util.List.of(subCategory));
        when(subCategoryMapper.toResponse(subCategory)).thenReturn(response);

        java.util.List<SubCategoryResponse> result = subCategoryService.getSubCategoriesByCategoryId(catId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(catId, result.get(0).getCategoryId());
        verify(subCategoryRepository).findByCategory_CategoryIdAndIsActiveTrueAndIsDeletedFalse(catId);
    }

    @Test
    void getSubCategoriesByCategoryId_WhenEmpty_ReturnsEmptyList() {
        UUID catId = category.getCategoryId();
        when(subCategoryRepository.findByCategory_CategoryIdAndIsActiveTrueAndIsDeletedFalse(catId))
                .thenReturn(java.util.Collections.emptyList());

        java.util.List<SubCategoryResponse> result = subCategoryService.getSubCategoriesByCategoryId(catId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(subCategoryRepository).findByCategory_CategoryIdAndIsActiveTrueAndIsDeletedFalse(catId);
    }

    @Test
    void updateSubCategory_WhenZeroStepTemplates_SetsSlaToZero() {
        UUID subCatId = subCategory.getSubCategoryId();
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(Optional.of(subCategory));
        when(categoryRepository.findByCategoryIdAndIsDeletedFalse(request.getCategoryId())).thenReturn(Optional.of(category));
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(java.util.Collections.emptyList());
        when(subCategoryRepository.save(any(SubCategory.class))).thenReturn(subCategory);
        when(subCategoryMapper.toResponse(subCategory)).thenReturn(response);

        SubCategoryResponse result = subCategoryService.updateSubCategory(subCatId, request);

        assertNotNull(result);
        assertEquals(0, subCategory.getSla());
        verify(subCategoryRepository).save(subCategory);
    }

    @Test
    void updateSubCategory_SubCategoryNotFound_ThrowsSubcategoryNotFound() {
        UUID unknownSubId = UUID.randomUUID();
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(unknownSubId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> subCategoryService.updateSubCategory(unknownSubId, request));
        assertEquals(ErrorCode.SUBCATEGORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void updateSubCategory_CategoryNotFound_ThrowsCategoryNotFound() {
        UUID subCatId = subCategory.getSubCategoryId();
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(Optional.of(subCategory));
        when(categoryRepository.findByCategoryIdAndIsDeletedFalse(request.getCategoryId())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> subCategoryService.updateSubCategory(subCatId, request));
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void deleteSubCategory_ExistingId_MarksInactiveAndSoftDeleted() {
        UUID subCatId = subCategory.getSubCategoryId();
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(Optional.of(subCategory));

        subCategoryService.deleteSubCategory(subCatId);

        assertFalse(subCategory.isActive());
        assertTrue(subCategory.isDeleted());
        assertNotNull(subCategory.getDeletedAt());
        verify(subCategoryRepository).save(subCategory);
    }

    @Test
    void deleteSubCategory_NotFound_ThrowsSubcategoryNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(unknownId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> subCategoryService.deleteSubCategory(unknownId));
        assertEquals(ErrorCode.SUBCATEGORY_NOT_FOUND, ex.getErrorCode());
        verify(subCategoryRepository, never()).save(any());
    }

    @Test
    void getSubCategory_ExistingId_ReturnsResponse() {
        UUID subCatId = subCategory.getSubCategoryId();
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(subCatId)).thenReturn(Optional.of(subCategory));
        when(subCategoryMapper.toResponse(subCategory)).thenReturn(response);

        SubCategoryResponse result = subCategoryService.getSubCategory(subCatId);

        assertNotNull(result);
        assertEquals(subCatId, result.getSubCategoryId());
    }

    @Test
    void getSubCategory_NotFound_ThrowsSubcategoryNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(unknownId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> subCategoryService.getSubCategory(unknownId));
        assertEquals(ErrorCode.SUBCATEGORY_NOT_FOUND, ex.getErrorCode());
    }
}
