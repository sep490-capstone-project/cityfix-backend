package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.CategoryRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.CategoryResponse;
import com.cityfix.backend.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private AdminCategoryController adminCategoryController;

    private UUID categoryId;
    private CategoryRequest validRequest;
    private CategoryResponse mockResponse;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();

        validRequest = CategoryRequest.builder()
                .name("Hạ tầng")
                .description("Vấn đề hạ tầng")
                .sla(24)
                .isActive(true)
                .build();

        mockResponse = CategoryResponse.builder()
                .categoryId(categoryId)
                .name("Hạ tầng")
                .build();
    }

    @Test
    @DisplayName("getAllCategories_returns200OK")
    void getAllCategories_returns200OK() {
        when(categoryService.getAllCategories()).thenReturn(List.of(mockResponse));

        ResponseEntity<BaseResponse<List<CategoryResponse>>> response = adminCategoryController.getAllCategories();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("createCategory_validRequest_returns200OK")
    void createCategory_validRequest_returns200OK() {
        when(categoryService.createCategory(any(CategoryRequest.class))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<CategoryResponse>> response = adminCategoryController.createCategory(validRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(categoryService, times(1)).createCategory(validRequest);
    }

    @Test
    @DisplayName("updateCategory_validRequest_returns200OK")
    void updateCategory_validRequest_returns200OK() {
        when(categoryService.updateCategory(eq(categoryId), any(CategoryRequest.class))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<CategoryResponse>> response = adminCategoryController.updateCategory(categoryId, validRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(categoryService, times(1)).updateCategory(categoryId, validRequest);
    }

    @Test
    @DisplayName("deleteCategory_validId_returns200OK")
    void deleteCategory_validId_returns200OK() {
        doNothing().when(categoryService).deleteCategory(categoryId);

        ResponseEntity<BaseResponse<Void>> response = adminCategoryController.deleteCategory(categoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(categoryService, times(1)).deleteCategory(categoryId);
    }
}
