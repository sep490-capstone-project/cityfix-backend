package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.request.StepRequest;
import com.cityfix.backend.model.dto.response.StepResponse;
import com.cityfix.backend.model.entity.Step;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface StepMapper {
    
    @Mapping(target = "subCategoryId", source = "subCategory.subCategoryId")
    StepResponse toResponse(Step step);
    
    @Mapping(target = "subCategory", ignore = true)
    @Mapping(target = "stepId", ignore = true)
    Step toEntity(StepRequest request);

    @Mapping(target = "subCategory", ignore = true)
    @Mapping(target = "stepId", ignore = true)
    void updateEntityFromRequest(StepRequest request, @MappingTarget Step step);
}
