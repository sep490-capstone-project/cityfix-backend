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
        when(categoryRepository.findById(request.getCategoryId())).thenReturn(Optional.of(category));
        when(subCategoryMapper.toEntity(request)).thenReturn(subCategory);
        when(subCategoryRepository.save(any(SubCategory.class))).thenReturn(subCategory);
        when(subCategoryMapper.toResponse(subCategory)).thenReturn(response);

        SubCategoryResponse result = subCategoryService.createSubCategory(request);

        assertNotNull(result);
        assertEquals("Test", result.getName());
        verify(subCategoryRepository).save(any(SubCategory.class));
    }

    @Test
    void createSubCategory_CategoryNotFound() {
        when(categoryRepository.findById(request.getCategoryId())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> subCategoryService.createSubCategory(request));
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, ex.getErrorCode());
    }
}
