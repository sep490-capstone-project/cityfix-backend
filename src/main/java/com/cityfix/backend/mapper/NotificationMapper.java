package com.cityfix.backend.mapper;

import com.cityfix.backend.model.entity.Notification;
import com.cityfix.backend.model.dto.response.NotificationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "isRead", target = "isRead")
    NotificationResponse toResponse(Notification notification);
}
