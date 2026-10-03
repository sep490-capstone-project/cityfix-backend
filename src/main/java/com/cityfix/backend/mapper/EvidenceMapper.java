package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse;
import com.cityfix.backend.model.entity.ResolutionEvidence;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EvidenceMapper {

    @Mapping(target = "staffName", source = "staff.fullName")
    ResolutionEvidenceResponse toResolutionEvidenceResponse(ResolutionEvidence evidence);

    List<ResolutionEvidenceResponse> toResponseList(List<ResolutionEvidence> evidences);
}
