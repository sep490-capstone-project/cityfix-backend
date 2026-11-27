package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.TicketHistoryResponse;
import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketHistory;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.TicketStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketHistoryMapperTest {

    private TicketHistoryMapperImpl ticketHistoryMapper;

    private User reporter;
    private User staff;
    private Ticket anonymousTicket;

    @BeforeEach
    void setUp() {
        ticketHistoryMapper = new TicketHistoryMapperImpl();

        reporter = new User();
        reporter.setUserId(UUID.randomUUID());
        reporter.setEmail("reporter@cityfix.vn");
        reporter.setFullName("Nguyễn Văn Ẩn");
        reporter.setRole(new Role(UUID.randomUUID(), "RESIDENT"));

        staff = new User();
        staff.setUserId(UUID.randomUUID());
        staff.setEmail("staff@cityfix.vn");
        staff.setFullName("Trần Cán Bộ");
        staff.setRole(new Role(UUID.randomUUID(), "STAFF"));

        anonymousTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .user(reporter)
                .isAnonymous(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(User principal, String authority) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(authority))
        );
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    @DisplayName("toTicketHistoryResponse_anonymousTicket_reporterActor_unauthenticated_masksActor")
    void toTicketHistoryResponse_anonymousTicket_reporterActor_unauthenticated_masksActor() {
        SecurityContextHolder.clearContext();
        TicketHistory history = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .ticket(anonymousTicket)
                .actor(reporter)
                .actionType(ActionType.ROUTED)
                .currentStatus(TicketStatus.INITIATED)
                .actionNote("Chuyển sang duyệt thủ công")
                .build();

        TicketHistoryResponse response = ticketHistoryMapper.toTicketHistoryResponse(history);

        assertThat(response).isNotNull();
        assertThat(response.getActorId()).isNull();
        assertThat(response.getActorName()).isNull();
        assertThat(response.getActionNote()).isEqualTo("Chuyển sang duyệt thủ công");
    }

    @Test
    @DisplayName("toTicketHistoryResponse_anonymousTicket_reporterActor_residentOrManager_masksActor")
    void toTicketHistoryResponse_anonymousTicket_reporterActor_residentOrManager_masksActor() {
        User manager = new User();
        manager.setUserId(UUID.randomUUID());
        manager.setRole(new Role(UUID.randomUUID(), "MANAGER"));
        mockSecurityContext(manager, "ROLE_MANAGER");

        TicketHistory history = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .ticket(anonymousTicket)
                .actor(reporter)
                .actionType(ActionType.CANCELLED)
                .previousStatus(TicketStatus.INITIATED)
                .currentStatus(TicketStatus.CANCELLED)
                .actionNote("Người dân tự hủy vé")
                .build();

        TicketHistoryResponse response = ticketHistoryMapper.toTicketHistoryResponse(history);

        assertThat(response.getActorId()).isNull();
        assertThat(response.getActorName()).isNull();
    }

    @Test
    @DisplayName("toTicketHistoryResponse_anonymousTicket_reporterActor_anonymousToken_masksActor")
    void toTicketHistoryResponse_anonymousTicket_reporterActor_anonymousToken_masksActor() {
        AnonymousAuthenticationToken anonAuth = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(anonAuth);
        SecurityContextHolder.setContext(ctx);

        TicketHistory history = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .ticket(anonymousTicket)
                .actor(reporter)
                .actionType(ActionType.ROUTED)
                .build();

        TicketHistoryResponse response = ticketHistoryMapper.toTicketHistoryResponse(history);

        assertThat(response.getActorId()).isNull();
        assertThat(response.getActorName()).isNull();
    }

    @Test
    @DisplayName("toTicketHistoryResponse_anonymousTicket_reporterActor_admin_preservesActor")
    void toTicketHistoryResponse_anonymousTicket_reporterActor_admin_preservesActor() {
        User admin = new User();
        admin.setUserId(UUID.randomUUID());
        admin.setRole(new Role(UUID.randomUUID(), "ADMIN"));
        mockSecurityContext(admin, "ROLE_ADMIN");

        TicketHistory history = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .ticket(anonymousTicket)
                .actor(reporter)
                .actionType(ActionType.ROUTED)
                .build();

        TicketHistoryResponse response = ticketHistoryMapper.toTicketHistoryResponse(history);

        assertThat(response.getActorId()).isEqualTo(reporter.getUserId());
        assertThat(response.getActorName()).isEqualTo("Nguyễn Văn Ẩn");
    }

    @Test
    @DisplayName("toTicketHistoryResponse_anonymousTicket_staffActor_preservesActor")
    void toTicketHistoryResponse_anonymousTicket_staffActor_preservesActor() {
        User resident = new User();
        resident.setUserId(UUID.randomUUID());
        resident.setRole(new Role(UUID.randomUUID(), "RESIDENT"));
        mockSecurityContext(resident, "ROLE_RESIDENT");

        TicketHistory history = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .ticket(anonymousTicket)
                .actor(staff)
                .actionType(ActionType.WORK_STARTED)
                .build();

        TicketHistoryResponse response = ticketHistoryMapper.toTicketHistoryResponse(history);

        assertThat(response.getActorId()).isEqualTo(staff.getUserId());
        assertThat(response.getActorName()).isEqualTo("Trần Cán Bộ");
    }

    @Test
    @DisplayName("toTicketHistoryResponse_nonAnonymousTicket_reporterActor_preservesActor")
    void toTicketHistoryResponse_nonAnonymousTicket_reporterActor_preservesActor() {
        anonymousTicket.setIsAnonymous(false);
        SecurityContextHolder.clearContext();

        TicketHistory history = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .ticket(anonymousTicket)
                .actor(reporter)
                .actionType(ActionType.ROUTED)
                .build();

        TicketHistoryResponse response = ticketHistoryMapper.toTicketHistoryResponse(history);

        assertThat(response.getActorId()).isEqualTo(reporter.getUserId());
        assertThat(response.getActorName()).isEqualTo("Nguyễn Văn Ẩn");
    }

    @Test
    @DisplayName("toTicketHistoryResponseList_anonymousTicket_masksOnlyReporterEntries")
    void toTicketHistoryResponseList_anonymousTicket_masksOnlyReporterEntries() {
        SecurityContextHolder.clearContext();

        TicketHistory reporterEntry = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .ticket(anonymousTicket)
                .actor(reporter)
                .actionType(ActionType.ROUTED)
                .build();
        TicketHistory staffEntry = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .ticket(anonymousTicket)
                .actor(staff)
                .actionType(ActionType.WORK_STARTED)
                .build();

        List<TicketHistoryResponse> responses = ticketHistoryMapper.toTicketHistoryResponseList(
                List.of(staffEntry, reporterEntry)
        );

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getActorId()).isEqualTo(staff.getUserId());
        assertThat(responses.get(0).getActorName()).isEqualTo("Trần Cán Bộ");
        assertThat(responses.get(1).getActorId()).isNull();
        assertThat(responses.get(1).getActorName()).isNull();
    }
}
