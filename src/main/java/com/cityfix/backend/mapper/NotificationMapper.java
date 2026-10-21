package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.NotificationResponse;
import com.cityfix.backend.model.entity.UserNotification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(source = "notification.notificationId", target = "notificationId")
    @Mapping(target = "ticketId", expression = "java(mapTicketId(userNotification))")
    @Mapping(source = "notification.title", target = "title")
    @Mapping(source = "notification.content", target = "content")
    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "isRead", target = "isRead")
    @Mapping(source = "readAt", target = "readAt")
    @Mapping(source = "createdAt", target = "createdAt")
    NotificationResponse toResponse(UserNotification userNotification);

    default UUID mapTicketId(UserNotification userNotification) {
        if (userNotification == null || userNotification.getNotification() == null) {
            return null;
        }
        if (userNotification.getNotification().getTicket() == null) {
            return null;
        }
        try {
            return userNotification.getNotification().getTicket().getTicketId();
        } catch (Exception e) {
            return null;
        }
    }
}
