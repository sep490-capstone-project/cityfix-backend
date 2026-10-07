package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.request.SubCategoryRequest;
import com.cityfix.backend.model.dto.response.SubCategoryResponse;
import com.cityfix.backend.model.entity.SubCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubCategoryMapper {
    @Mapping(target = "categoryId", source = "category.categoryId")
    @Mapping(target = "isActive", source = "active")
    SubCategoryResponse toResponse(SubCategory subCategory);
    
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "subCategoryId", ignore = true)
    @Mapping(target = "isActive", source = "isActive")
    SubCategory toEntity(SubCategoryRequest request);
}
