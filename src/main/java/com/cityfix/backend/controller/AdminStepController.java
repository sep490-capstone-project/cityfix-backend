package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.StepRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.StepResponse;
import com.cityfix.backend.service.StepService;
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
@RequestMapping("/api/v1/admin/steps")
@RequiredArgsConstructor
@Tag(name = "ADMIN Step API", description = "CRUD operations for Steps")
public class AdminStepController {

    private final StepService stepService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new Step")
    public ResponseEntity<BaseResponse<StepResponse>> createStep(@Valid @RequestBody StepRequest request) {
        return ResponseEntity.ok(BaseResponse.success("Created successfully", stepService.createStep(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an existing Step")
    public ResponseEntity<BaseResponse<StepResponse>> updateStep(
            @PathVariable UUID id, @Valid @RequestBody StepRequest request) {
        return ResponseEntity.ok(BaseResponse.success("Updated successfully", stepService.updateStep(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a Step (Hard Delete)")
    public ResponseEntity<BaseResponse<Void>> deleteStep(@PathVariable UUID id) {
        stepService.deleteStep(id);
        return ResponseEntity.ok(BaseResponse.success("Deleted successfully", null));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all Steps")
    public ResponseEntity<BaseResponse<List<StepResponse>>> getAllSteps() {
        return ResponseEntity.ok(BaseResponse.success("Success", stepService.getAllSteps()));
    }

    @GetMapping("/sub-category/{subCategoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Get Steps by SubCategory ID")
    public ResponseEntity<BaseResponse<List<StepResponse>>> getStepsBySubCategory(@PathVariable UUID subCategoryId) {
        return ResponseEntity.ok(BaseResponse.success("Success", stepService.getStepsBySubCategoryId(subCategoryId)));
    }
}
