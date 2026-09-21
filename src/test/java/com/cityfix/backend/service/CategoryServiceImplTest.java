package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.model.dto.request.CategoryRequest;
import com.cityfix.backend.model.dto.response.CategoryResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.repository.CategoryRepository;
import com.cityfix.backend.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private UUID categoryId;
    private CategoryRequest categoryRequest;
    private Category category;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();

        categoryRequest = CategoryRequest.builder()
                .name("Hạ tầng")
                .description("Vấn đề hạ tầng")
                .sla(24)
                .isActive(true)
                .build();

        category = Category.builder()
                .categoryId(categoryId)
                .name("Hạ tầng")
                .description("Vấn đề hạ tầng")
                .sla(24)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("getAllCategories_returnsList")
    void getAllCategories_returnsList() {
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of(category));

        List<CategoryResponse> result = categoryService.getAllCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Hạ tầng");
    }

    @Test
    @DisplayName("createCategory_validRequest_returnsResponse")
    void createCategory_validRequest_returnsResponse() {
        when(categoryRepository.findByNameAndIsDeletedFalse(categoryRequest.getName())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        CategoryResponse result = categoryService.createCategory(categoryRequest);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Hạ tầng");
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    @DisplayName("createCategory_duplicateName_throwsException")
    void createCategory_duplicateName_throwsException() {
        when(categoryRepository.findByNameAndIsDeletedFalse(categoryRequest.getName())).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.createCategory(categoryRequest))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Tên danh mục đã tồn tại");
    }

    @Test
    @DisplayName("updateCategory_validRequest_returnsResponse")
    void updateCategory_validRequest_returnsResponse() {
        when(categoryRepository.findByCategoryIdAndIsDeletedFalse(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameAndIsDeletedFalse(categoryRequest.getName())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        CategoryResponse result = categoryService.updateCategory(categoryId, categoryRequest);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Hạ tầng");
    }

    @Test
    @DisplayName("updateCategory_notFound_throwsException")
    void updateCategory_notFound_throwsException() {
        when(categoryRepository.findByCategoryIdAndIsDeletedFalse(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, categoryRequest))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không tìm thấy danh mục");
    }

    @Test
    @DisplayName("updateCategory_duplicateName_throwsException")
    void updateCategory_duplicateName_throwsException() {
        Category otherCategory = Category.builder().categoryId(UUID.randomUUID()).name("Hạ tầng").build();

        when(categoryRepository.findByCategoryIdAndIsDeletedFalse(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameAndIsDeletedFalse(categoryRequest.getName())).thenReturn(Optional.of(otherCategory));

        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, categoryRequest))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Tên danh mục đã tồn tại");
    }

    @Test
    @DisplayName("deleteCategory_validId_setsDeletedTrue")
    void deleteCategory_validId_setsDeletedTrue() {
        when(categoryRepository.findByCategoryIdAndIsDeletedFalse(categoryId)).thenReturn(Optional.of(category));

        categoryService.deleteCategory(categoryId);

        assertThat(category.isDeleted()).isTrue();
        verify(categoryRepository, times(1)).save(category);
    }
}
