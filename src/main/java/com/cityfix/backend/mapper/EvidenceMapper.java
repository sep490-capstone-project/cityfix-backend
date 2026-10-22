package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse;
import com.cityfix.backend.model.entity.ResolutionEvidence;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EvidenceMapper {

    @Mapping(target = "staffName", source = "staff.fullName")
    @Mapping(target = "ticketStepId", source = "ticketStep.id")
    @Mapping(target = "createdAt", source = "createdAt")
    ResolutionEvidenceResponse toResolutionEvidenceResponse(ResolutionEvidence evidence);

    List<ResolutionEvidenceResponse> toResponseList(List<ResolutionEvidence> evidences);

    @Mapping(target = "evidenceId", ignore = true)
    @Mapping(target = "ticketStep", source = "ticketStep")
    @Mapping(target = "staff", source = "staff")
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "resolutionNote", source = "resolutionNote")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    ResolutionEvidence toResolutionEvidence(TicketStep ticketStep, User staff, String imageUrl, String resolutionNote);
}
