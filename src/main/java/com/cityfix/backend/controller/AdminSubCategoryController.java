package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.SubCategoryRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.SubCategoryResponse;
import com.cityfix.backend.service.SubCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/sub-categories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "ADMIN SubCategory API", description = "CRUD operations for SubCategories")
public class AdminSubCategoryController {

    private final SubCategoryService subCategoryService;

    @PostMapping
    @Operation(summary = "Create a new SubCategory")
    public ResponseEntity<BaseResponse<SubCategoryResponse>> createSubCategory(@Valid @RequestBody SubCategoryRequest request) {
        return ResponseEntity.ok(BaseResponse.success("Created successfully", subCategoryService.createSubCategory(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing SubCategory")
    public ResponseEntity<BaseResponse<SubCategoryResponse>> updateSubCategory(
            @PathVariable UUID id, @Valid @RequestBody SubCategoryRequest request) {
        return ResponseEntity.ok(BaseResponse.success("Updated successfully", subCategoryService.updateSubCategory(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a SubCategory (Soft Delete)")
    public ResponseEntity<BaseResponse<Void>> deleteSubCategory(@PathVariable UUID id) {
        subCategoryService.deleteSubCategory(id);
        return ResponseEntity.ok(BaseResponse.success("Deleted successfully", null));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a SubCategory by ID")
    public ResponseEntity<BaseResponse<SubCategoryResponse>> getSubCategory(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.success("Success", subCategoryService.getSubCategory(id)));
    }

    @GetMapping
    @Operation(summary = "Get all SubCategories")
    public ResponseEntity<BaseResponse<List<SubCategoryResponse>>> getAllSubCategories() {
        return ResponseEntity.ok(BaseResponse.success("Success", subCategoryService.getAllSubCategories()));
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Get SubCategories by Category ID")
    public ResponseEntity<BaseResponse<List<SubCategoryResponse>>> getSubCategoriesByCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(BaseResponse.success("Success", subCategoryService.getSubCategoriesByCategoryId(categoryId)));
    }
}
