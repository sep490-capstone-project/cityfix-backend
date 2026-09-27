package com.cityfix.backend.mapper;

import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketHistory;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.TicketStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketHistoryMapper {

    @Mapping(target = "historyId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "ticket", source = "ticket")
    @Mapping(target = "actor", source = "actor")
    @Mapping(target = "actionType", source = "actionType")
    @Mapping(target = "previousStatus", source = "previousStatus")
    @Mapping(target = "currentStatus", source = "currentStatus")
    @Mapping(target = "actionNote", source = "actionNote")
    TicketHistory toTicketHistory(
            Ticket ticket,
            User actor,
            ActionType actionType,
            TicketStatus previousStatus,
            TicketStatus currentStatus,
            String actionNote
    );
}
