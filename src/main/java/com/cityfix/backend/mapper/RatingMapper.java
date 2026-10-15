package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.RatingResponse;
import com.cityfix.backend.model.entity.Rating;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface RatingMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "ticketId", source = "ticket.ticketId")
    @Mapping(target = "userId", source = "user.userId")
    @Mapping(target = "userName", source = "user.fullName")
    @Mapping(target = "rate", source = "rate")
    @Mapping(target = "isAnonymous", source = "isAnonymous")
    @Mapping(target = "feedback", source = "feedback")
    @Mapping(target = "createdAt", source = "createdAt")
    RatingResponse toResponse(Rating rating);

    @AfterMapping
    default void maskAnonymous(Rating rating, @MappingTarget RatingResponse response) {
        if (rating == null || response == null) {
            return;
        }
        if (Boolean.TRUE.equals(rating.getIsAnonymous())) {
            response.setUserId(null);
            response.setUserName(null);
        }
    }
}
