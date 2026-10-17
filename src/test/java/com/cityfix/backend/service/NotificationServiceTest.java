package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.NotificationMapper;
import com.cityfix.backend.model.dto.response.NotificationResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Notification;
import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.Step;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.NotificationRepository;
import com.cityfix.backend.repository.TicketVoteRepository;
import com.cityfix.backend.repository.UserDepartmentRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private TicketVoteRepository ticketVoteRepository;
    @Mock
    private UserDepartmentRepository userDepartmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User reporter;
    private User upvoter1;
    private User upvoter2;
    private User staff;
    private User manager1;
    private User manager2;
    private Department department;
    private Ticket ticket;
    private Step stepDef;
    private TicketStep ticketStep;

    @BeforeEach
    void setUp() {
        Role citizenRole = new Role(UUID.randomUUID(), "CITIZEN");
        Role staffRole = new Role(UUID.randomUUID(), "STAFF");
        Role managerRole = new Role(UUID.randomUUID(), "MANAGER");

        reporter = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Người Báo Cáo")
                .email("reporter@cityfix.com")
                .role(citizenRole)
                .status(UserStatus.ACTIVE)
                .build();

        upvoter1 = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Người Upvote 1")
                .email("upvoter1@cityfix.com")
                .role(citizenRole)
                .status(UserStatus.ACTIVE)
                .build();

        upvoter2 = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Người Upvote 2")
                .email("upvoter2@cityfix.com")
                .role(citizenRole)
                .status(UserStatus.ACTIVE)
                .build();

        manager1 = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Quản Lý 1")
                .email("manager1@cityfix.com")
                .role(managerRole)
                .status(UserStatus.ACTIVE)
                .build();

        manager2 = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Quản Lý 2")
                .email("manager2@cityfix.com")
                .role(managerRole)
                .status(UserStatus.ACTIVE)
                .build();

        department = Department.builder()
                .departmentId(UUID.randomUUID())
                .name("Phòng Hạ tầng Kỹ thuật")
                .manager(manager1)
                .build();

        staff = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Nhân Viên Xử Lý")
                .email("staff@cityfix.com")
                .role(staffRole)
                .department(department)
                .status(UserStatus.ACTIVE)
                .build();

        ticket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .user(reporter)
                .address("123 Nguyễn Huệ, Quận 1, TP.HCM")
                .build();

        stepDef = Step.builder()
                .stepId(UUID.randomUUID())
                .stepName("Khảo sát hiện trường")
                .build();

        ticketStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .step(stepDef)
                .assignee(staff)
                .status(AssignmentStatus.IN_PROGRESS)
                .build();
    }

    private void mockCurrentUser(User user) {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList())
        );
    }

    @Test
    @DisplayName("notifyStepStarted_sendsToReporterUpvotersAndSameDepartmentManagers_andDeduplicates")
    void notifyStepStarted_sendsToReporterUpvotersAndSameDepartmentManagers_andDeduplicates() {
        // Reporter is also in upvoters list; manager1 is both department.manager and in userDepartmentRepository
        when(ticketVoteRepository.findUpvotersByTicketId(ticket.getTicketId()))
                .thenReturn(List.of(reporter, upvoter1, upvoter2));
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId()))
                .thenReturn(List.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any()))
                .thenReturn(List.of(manager1, manager2));
        when(userRepository.findActiveManagersByDepartmentIds(any()))
                .thenReturn(List.of(manager1));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationMapper.toResponse(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    return NotificationResponse.builder()
                            .userId(n.getUser().getUserId())
                            .title(n.getTitle())
                            .content(n.getContent())
                            .isRead(n.isRead())
                            .build();
                });

        notificationService.notifyStepStarted(ticketStep, staff);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(5)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();
        List<UUID> recipientIds = saved.stream().map(n -> n.getUser().getUserId()).toList();
        assertThat(recipientIds).containsExactly(
                reporter.getUserId(),
                upvoter1.getUserId(),
                upvoter2.getUserId(),
                manager1.getUserId(),
                manager2.getUserId()
        );

        // Check reporter content
        assertThat(saved.get(0).getTitle()).isEqualTo("Tiến độ sự cố: Bắt đầu bước xử lý");
        assertThat(saved.get(0).getContent()).contains("Sự cố của bạn").contains("Nhân Viên Xử Lý").contains("Khảo sát hiện trường");

        // Check upvoter content
        assertThat(saved.get(1).getContent()).contains("Sự cố bạn theo dõi").contains("Nhân Viên Xử Lý").contains("Khảo sát hiện trường");

        // Check manager content
        assertThat(saved.get(3).getTitle()).isEqualTo("Tiến độ xử lý của nhân viên: Bắt đầu bước xử lý");
        assertThat(saved.get(3).getContent()).contains("Nhân viên Nhân Viên Xử Lý đã bắt đầu thực hiện bước 'Khảo sát hiện trường'");

        // Verify WebSocket pushes
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/" + reporter.getUserId()), any(NotificationResponse.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/" + upvoter1.getUserId()), any(NotificationResponse.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/" + upvoter2.getUserId()), any(NotificationResponse.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/" + manager1.getUserId()), any(NotificationResponse.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/" + manager2.getUserId()), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("notifyStepCompleted_intermediateStep_sendsStepCompletedMessage")
    void notifyStepCompleted_intermediateStep_sendsStepCompletedMessage() {
        when(ticketVoteRepository.findUpvotersByTicketId(ticket.getTicketId()))
                .thenReturn(List.of(upvoter1));
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId()))
                .thenReturn(List.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any()))
                .thenReturn(List.of(manager1));
        when(userRepository.findActiveManagersByDepartmentIds(any()))
                .thenReturn(Collections.emptyList());
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyStepCompleted(ticketStep, staff, false);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(3)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();
        assertThat(saved.get(0).getTitle()).isEqualTo("Tiến độ sự cố: Hoàn thành bước xử lý");
        assertThat(saved.get(0).getContent()).contains("trong sự cố của bạn đã được nhân viên Nhân Viên Xử Lý hoàn thành");
        assertThat(saved.get(1).getContent()).contains("của sự cố bạn theo dõi đã được nhân viên Nhân Viên Xử Lý hoàn thành");
        assertThat(saved.get(2).getTitle()).isEqualTo("Tiến độ xử lý của nhân viên: Hoàn thành bước xử lý");
        assertThat(saved.get(2).getContent()).contains("Nhân viên Nhân Viên Xử Lý đã hoàn thành bước 'Khảo sát hiện trường'");
    }

    @Test
    @DisplayName("notifyStepCompleted_finalStepResolved_sendsSingleUnifiedResolvedMessage")
    void notifyStepCompleted_finalStepResolved_sendsSingleUnifiedResolvedMessage() {
        when(ticketVoteRepository.findUpvotersByTicketId(ticket.getTicketId()))
                .thenReturn(List.of(upvoter1));
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId()))
                .thenReturn(List.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any()))
                .thenReturn(List.of(manager1));
        when(userRepository.findActiveManagersByDepartmentIds(any()))
                .thenReturn(Collections.emptyList());
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyStepCompleted(ticketStep, staff, true);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(3)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();
        assertThat(saved.get(0).getTitle()).isEqualTo("Sự cố đã được giải quyết");
        assertThat(saved.get(0).getContent()).contains("Toàn bộ các bước xử lý của sự cố đã hoàn tất");
        assertThat(saved.get(1).getTitle()).isEqualTo("Sự cố đã được giải quyết");
        assertThat(saved.get(1).getContent()).contains("Sự cố bạn theo dõi đã được xử lý hoàn tất");
        assertThat(saved.get(2).getTitle()).isEqualTo("Sự cố đã được giải quyết hoàn tất");
        assertThat(saved.get(2).getContent()).contains("Toàn bộ sự cố đã chuyển sang trạng thái Đã giải quyết");
    }

    @Test
    @DisplayName("getMyNotifications_returnsPagedNotificationsForCurrentUser")
    void getMyNotifications_returnsPagedNotificationsForCurrentUser() {
        mockCurrentUser(manager1);
        Notification n = Notification.builder()
                .notificationId(UUID.randomUUID())
                .user(manager1)
                .title("Test")
                .content("Content")
                .isRead(false)
                .build();
        NotificationResponse resp = NotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .userId(manager1.getUserId())
                .title("Test")
                .content("Content")
                .isRead(false)
                .build();

        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(eq(manager1.getUserId()), any()))
                .thenReturn(new PageImpl<>(List.of(n)));
        when(notificationMapper.toResponse(n)).thenReturn(resp);

        Page<NotificationResponse> result = notificationService.getMyNotifications(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(manager1.getUserId());
    }

    @Test
    @DisplayName("markAsRead_whenNotificationBelongsToAnotherUser_throwsForbidden")
    void markAsRead_whenNotificationBelongsToAnotherUser_throwsForbidden() {
        mockCurrentUser(upvoter1);
        UUID notifId = UUID.randomUUID();
        Notification n = Notification.builder()
                .notificationId(notifId)
                .user(reporter)
                .title("Test")
                .content("Content")
                .isRead(false)
                .build();

        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markAsRead(notifId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("markAsRead_whenNotificationBelongsToUser_marksReadAndSaves")
    void markAsRead_whenNotificationBelongsToUser_marksReadAndSaves() {
        mockCurrentUser(reporter);
        UUID notifId = UUID.randomUUID();
        Notification n = Notification.builder()
                .notificationId(notifId)
                .user(reporter)
                .title("Test")
                .content("Content")
                .isRead(false)
                .build();

        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationMapper.toResponse(any(Notification.class))).thenReturn(
                NotificationResponse.builder().notificationId(notifId).isRead(true).build()
        );

        NotificationResponse res = notificationService.markAsRead(notifId);

        assertThat(n.isRead()).isTrue();
        assertThat(res.isRead()).isTrue();
        verify(notificationRepository).save(n);
    }

    @Test
    @DisplayName("markAllAsRead_invokesRepositoryMarkAllAsReadByUserId")
    void markAllAsRead_invokesRepositoryMarkAllAsReadByUserId() {
        mockCurrentUser(reporter);
        when(notificationRepository.markAllAsReadByUserId(reporter.getUserId())).thenReturn(3);

        notificationService.markAllAsRead();

        verify(notificationRepository).markAllAsReadByUserId(reporter.getUserId());
    }

    @Test
    @DisplayName("getUnreadCount_returnsUnreadCountFromRepository")
    void getUnreadCount_returnsUnreadCountFromRepository() {
        mockCurrentUser(manager1);
        when(notificationRepository.countByUser_UserIdAndIsReadFalse(manager1.getUserId())).thenReturn(4L);

        long count = notificationService.getUnreadCount();

        assertThat(count).isEqualTo(4L);
        verify(notificationRepository).countByUser_UserIdAndIsReadFalse(manager1.getUserId());
    }
}
