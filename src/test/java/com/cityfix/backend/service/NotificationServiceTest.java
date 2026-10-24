package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.NotificationMapper;
import com.cityfix.backend.model.dto.request.CreateStaffRequest;
import com.cityfix.backend.model.dto.response.NotificationResponse;
import com.cityfix.backend.model.dto.response.StaffRequestResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Notification;
import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.Step;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.UserNotification;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.repository.NotificationRepository;
import com.cityfix.backend.repository.TicketHistoryRepository;
import com.cityfix.backend.repository.TicketRepository;
import com.cityfix.backend.repository.TicketStepRepository;
import com.cityfix.backend.repository.TicketVoteRepository;
import com.cityfix.backend.repository.UserDepartmentRepository;
import com.cityfix.backend.repository.UserNotificationRepository;
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

import java.time.ZonedDateTime;
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
    private UserNotificationRepository userNotificationRepository;
    @Mock
    private TicketVoteRepository ticketVoteRepository;
    @Mock
    private UserDepartmentRepository userDepartmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private TicketHistoryRepository ticketHistoryRepository;
    @Mock
    private TicketStepRepository ticketStepRepository;
    @Mock
    private TicketRepository ticketRepository;
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
                .name("Phòng Quản Lý Đô Thị")
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
                .address("123 Đường ABC")
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
        when(ticketVoteRepository.findUpvotersByTicketId(ticket.getTicketId()))
                .thenReturn(List.of(reporter, upvoter1, upvoter2));
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId()))
                .thenReturn(List.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any()))
                .thenReturn(List.of(manager1, manager2));
        when(departmentRepository.findById(department.getDepartmentId()))
                .thenReturn(Optional.of(department));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationMapper.toResponse(any(UserNotification.class)))
                .thenAnswer(inv -> {
                    UserNotification un = inv.getArgument(0);
                    return NotificationResponse.builder()
                            .userId(un.getUser().getUserId())
                            .title(un.getNotification().getTitle())
                            .content(un.getNotification().getContent())
                            .isRead(un.isRead())
                            .build();
                });

        notificationService.notifyStepStarted(ticketStep, staff);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        // 1 notification for citizen group (reporter, upvoter1, upvoter2), 1 notification for manager group (manager1, manager2)
        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();

        // 1. Citizen Notification
        Notification citizenNotif = saved.get(0);
        assertThat(citizenNotif.getTitle()).isEqualTo("Tiến độ sự cố: Bắt đầu bước xử lý");
        assertThat(citizenNotif.getContent()).contains("Sự cố của bạn").contains("Nhân Viên Xử Lý").contains("Khảo sát hiện trường");
        List<UUID> citizenRecipientIds = citizenNotif.getRecipients().stream().map(un -> un.getUser().getUserId()).toList();
        assertThat(citizenRecipientIds).containsExactly(
                reporter.getUserId(),
                upvoter1.getUserId(),
                upvoter2.getUserId()
        );

        // 2. Manager Notification
        Notification managerNotif = saved.get(1);
        assertThat(managerNotif.getTitle()).isEqualTo("Tiến độ xử lý của nhân viên: Bắt đầu bước xử lý");
        assertThat(managerNotif.getContent()).contains("Nhân viên Nhân Viên Xử Lý đã bắt đầu thực hiện bước 'Khảo sát hiện trường'");
        List<UUID> managerRecipientIds = managerNotif.getRecipients().stream().map(un -> un.getUser().getUserId()).toList();
        assertThat(managerRecipientIds).containsExactly(
                manager1.getUserId(),
                manager2.getUserId()
        );

        // Verify WebSocket pushes to all 5 recipients
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
        when(departmentRepository.findById(department.getDepartmentId()))
                .thenReturn(Optional.of(department));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyStepCompleted(ticketStep, staff, false);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();
        // Citizen notification
        assertThat(saved.get(0).getTitle()).isEqualTo("Tiến độ sự cố: Hoàn thành bước xử lý");
        assertThat(saved.get(0).getContent()).contains("trong sự cố của bạn đã được nhân viên Nhân Viên Xử Lý hoàn thành");
        assertThat(saved.get(0).getRecipients()).hasSize(2); // reporter + upvoter1

        // Manager notification
        assertThat(saved.get(1).getTitle()).isEqualTo("Tiến độ xử lý của nhân viên: Hoàn thành bước xử lý");
        assertThat(saved.get(1).getContent()).contains("Nhân viên Nhân Viên Xử Lý đã hoàn thành bước 'Khảo sát hiện trường'");
        assertThat(saved.get(1).getRecipients()).hasSize(1); // manager1
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
        when(departmentRepository.findById(department.getDepartmentId()))
                .thenReturn(Optional.of(department));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyStepCompleted(ticketStep, staff, true);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();
        assertThat(saved.get(0).getTitle()).isEqualTo("Sự cố đã được giải quyết");
        assertThat(saved.get(0).getContent()).contains("Toàn bộ các bước xử lý của sự cố đã hoàn tất");
        assertThat(saved.get(1).getTitle()).isEqualTo("Sự cố đã được giải quyết hoàn tất");
        assertThat(saved.get(1).getContent()).contains("Toàn bộ sự cố đã chuyển sang trạng thái Đã giải quyết");
    }

    @Test
    @DisplayName("getMyNotifications_returnsPagedNotificationsForCurrentUser")
    void getMyNotifications_returnsPagedNotificationsForCurrentUser() {
        mockCurrentUser(manager1);
        Notification n = Notification.builder()
                .notificationId(UUID.randomUUID())
                .title("Test")
                .content("Content")
                .build();
        UserNotification un = UserNotification.builder()
                .id(UUID.randomUUID())
                .notification(n)
                .user(manager1)
                .isRead(false)
                .build();
        NotificationResponse resp = NotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .userId(manager1.getUserId())
                .title("Test")
                .content("Content")
                .isRead(false)
                .build();

        when(userNotificationRepository.findByUserIdOrderByCreatedAtDesc(eq(manager1.getUserId()), any()))
                .thenReturn(new PageImpl<>(List.of(un)));
        when(notificationMapper.toResponse(un)).thenReturn(resp);

        Page<NotificationResponse> result = notificationService.getMyNotifications(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(manager1.getUserId());
    }

    @Test
    @DisplayName("markAsRead_whenNotificationBelongsToAnotherUser_throwsForbidden")
    void markAsRead_whenNotificationBelongsToAnotherUser_throwsForbidden() {
        mockCurrentUser(upvoter1);
        UUID notifId = UUID.randomUUID();

        // User upvoter1 has no user_notification record for this notificationId
        when(userNotificationRepository.findByNotificationIdAndUserId(notifId, upvoter1.getUserId()))
                .thenReturn(Optional.empty());
        // But notification exists in the system
        when(notificationRepository.existsById(notifId)).thenReturn(true);

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
                .title("Test")
                .content("Content")
                .build();
        UserNotification un = UserNotification.builder()
                .id(UUID.randomUUID())
                .notification(n)
                .user(reporter)
                .isRead(false)
                .build();

        when(userNotificationRepository.findByNotificationIdAndUserId(notifId, reporter.getUserId()))
                .thenReturn(Optional.of(un));
        when(userNotificationRepository.save(any(UserNotification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationMapper.toResponse(any(UserNotification.class))).thenReturn(
                NotificationResponse.builder().notificationId(notifId).isRead(true).build()
        );

        NotificationResponse res = notificationService.markAsRead(notifId);

        assertThat(un.isRead()).isTrue();
        assertThat(un.getReadAt()).isNotNull();
        assertThat(res.isRead()).isTrue();
        verify(userNotificationRepository).save(un);
    }

    @Test
    @DisplayName("markAllAsRead_invokesRepositoryMarkAllAsReadByUserId")
    void markAllAsRead_invokesRepositoryMarkAllAsReadByUserId() {
        mockCurrentUser(reporter);
        when(userNotificationRepository.markAllAsReadByUserId(reporter.getUserId())).thenReturn(3);

        notificationService.markAllAsRead();

        verify(userNotificationRepository).markAllAsReadByUserId(reporter.getUserId());
    }

    @Test
    @DisplayName("getUnreadCount_returnsUnreadCountFromRepository")
    void getUnreadCount_returnsUnreadCountFromRepository() {
        mockCurrentUser(manager1);
        when(userNotificationRepository.countByUser_UserIdAndIsReadFalse(manager1.getUserId())).thenReturn(4L);

        long count = notificationService.getUnreadCount();

        assertThat(count).isEqualTo(4L);
        verify(userNotificationRepository).countByUser_UserIdAndIsReadFalse(manager1.getUserId());
    }

    @Test
    @DisplayName("notifyStepStarted_whenDepartmentIsDeleted_doesNotNotifyManagersOfDeletedDepartment")
    void notifyStepStarted_whenDepartmentIsDeleted_doesNotNotifyManagersOfDeletedDepartment() {
        Department deletedDept = Department.builder()
                .departmentId(UUID.randomUUID())
                .name("Phòng Đã Xóa")
                .build();
        deletedDept.setDeleted(true);

        User staffWithDeletedDept = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Nhân Viên")
                .role(new Role(UUID.randomUUID(), "STAFF"))
                .department(deletedDept)
                .status(UserStatus.ACTIVE)
                .build();

        when(departmentRepository.findById(deletedDept.getDepartmentId())).thenReturn(Optional.of(deletedDept));
        when(ticketVoteRepository.findUpvotersByTicketId(ticket.getTicketId())).thenReturn(Collections.emptyList());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyStepStarted(ticketStep, staffWithDeletedDept);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getRecipients().get(0).getUser().getUserId()).isEqualTo(reporter.getUserId());
    }

    @Test
    @DisplayName("notifyStepStarted_whenDepartmentNotFound_throwsDEPARTMENT_NOT_FOUND")
    void notifyStepStarted_whenDepartmentNotFound_throwsDEPARTMENT_NOT_FOUND() {
        UUID unknownDeptId = UUID.randomUUID();
        Department unknownDept = Department.builder()
                .departmentId(unknownDeptId)
                .build();

        User staffWithUnknownDept = User.builder()
                .userId(UUID.randomUUID())
                .role(new Role(UUID.randomUUID(), "STAFF"))
                .department(unknownDept)
                .status(UserStatus.ACTIVE)
                .build();

        when(departmentRepository.findById(unknownDeptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.notifyStepStarted(ticketStep, staffWithUnknownDept))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("createAndSendNotification_whenTicketIsNull_savesSystemNotification")
    void createAndSendNotification_whenTicketIsNull_savesSystemNotification() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationMapper.toResponse(any(UserNotification.class)))
                .thenReturn(NotificationResponse.builder().title("Thông báo chung").build());

        notificationService.createAndSendNotification(List.of(reporter), "Thông báo chung", "Nội dung bảo trì");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getTicket()).isNull();
        assertThat(saved.getTitle()).isEqualTo("Thông báo chung");
        assertThat(saved.getContent()).isEqualTo("Nội dung bảo trì");
        assertThat(saved.getRecipients()).hasSize(1);
        assertThat(saved.getRecipients().get(0).getUser().getUserId()).isEqualTo(reporter.getUserId());
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/" + reporter.getUserId()), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("createAndSendNotification_filtersOutDeletedOrInactiveRecipients")
    void createAndSendNotification_filtersOutDeletedOrInactiveRecipients() {
        User deletedUser = User.builder()
                .userId(UUID.randomUUID())
                .status(UserStatus.ACTIVE)
                .build();
        deletedUser.setDeleted(true);

        User blockedUser = User.builder()
                .userId(UUID.randomUUID())
                .status(UserStatus.BLOCKED)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createAndSendNotification(ticket, List.of(reporter, deletedUser, blockedUser), "Tiêu đề", "Nội dung");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getRecipients()).hasSize(1);
        assertThat(saved.getRecipients().get(0).getUser().getUserId()).isEqualTo(reporter.getUserId());
    }

    @Test
    @DisplayName("createAndSendNotification_whenRecipientsEmptyOrNull_doesNotSave")
    void createAndSendNotification_whenRecipientsEmptyOrNull_doesNotSave() {
        notificationService.createAndSendNotification(ticket, Collections.emptyList(), "Tiêu đề", "Nội dung");
        notificationService.createAndSendNotification(ticket, null, "Tiêu đề", "Nội dung");

        verify(notificationRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("createAndSendNotification_deduplicatesDuplicateRecipients")
    void createAndSendNotification_deduplicatesDuplicateRecipients() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createAndSendNotification(ticket, List.of(reporter, reporter), "Tiêu đề", "Nội dung");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getRecipients()).hasSize(1);
    }

    @Test
    @DisplayName("notifyRecurringTicketAlert_withSpecificManagersFromHistory_onlyNotifiesThoseManagers")
    void notifyRecurringTicketAlert_withSpecificManagersFromHistory_onlyNotifiesThoseManagers() {
        UUID oldTicketId = UUID.randomUUID();

        User manager1 = new User();
        manager1.setUserId(UUID.randomUUID());
        manager1.setStatus(UserStatus.ACTIVE);
        manager1.setRole(new Role(UUID.randomUUID(), "MANAGER"));

        when(ticketHistoryRepository.findActiveManagersByTicketIds(List.of(oldTicketId)))
                .thenReturn(List.of(manager1));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        ticket.setAddress("123 Lê Lợi, Q1");
        notificationService.notifyRecurringTicketAlert(ticket, 3, List.of(oldTicketId));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getTitle()).contains("Lần thứ 3");
        assertThat(saved.getContent()).contains("123 Lê Lợi, Q1");
        assertThat(saved.getRecipients()).hasSize(1);
        assertThat(saved.getRecipients().get(0).getUser().getUserId()).isEqualTo(manager1.getUserId());
        verify(userRepository, never()).findActiveManagers();
    }

    @Test
    @DisplayName("notifyRecurringTicketAlert_withActiveManagersFallback_createsAndSendsAlert")
    void notifyRecurringTicketAlert_withActiveManagersFallback_createsAndSendsAlert() {
        User manager1 = new User();
        manager1.setUserId(UUID.randomUUID());
        manager1.setStatus(UserStatus.ACTIVE);
        manager1.setRole(new Role(UUID.randomUUID(), "MANAGER"));

        User manager2 = new User();
        manager2.setUserId(UUID.randomUUID());
        manager2.setStatus(UserStatus.ACTIVE);
        manager2.setRole(new Role(UUID.randomUUID(), "MANAGER"));

        when(ticketHistoryRepository.findActiveManagersByTicketIds(any())).thenReturn(Collections.emptyList());
        when(ticketStepRepository.findByTicketTicketIdIn(any())).thenReturn(Collections.emptyList());
        when(userRepository.findActiveManagers()).thenReturn(List.of(manager1, manager2));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        ticket.setAddress("123 Lê Lợi, Q1");
        notificationService.notifyRecurringTicketAlert(ticket, 3, List.of(UUID.randomUUID()));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getTitle()).contains("Lần thứ 3");
        assertThat(saved.getContent()).contains("123 Lê Lợi, Q1");
        assertThat(saved.getContent()).contains("30 ngày");
        assertThat(saved.getRecipients()).hasSize(2);
    }

    @Test
    @DisplayName("notifyRecurringTicketAlert_noManagersFound_doesNotSave")
    void notifyRecurringTicketAlert_noManagersFound_doesNotSave() {
        when(ticketHistoryRepository.findActiveManagersByTicketIds(any())).thenReturn(Collections.emptyList());
        when(ticketStepRepository.findByTicketTicketIdIn(any())).thenReturn(Collections.emptyList());
        when(userRepository.findActiveManagers()).thenReturn(Collections.emptyList());

        notificationService.notifyRecurringTicketAlert(ticket, 3, List.of(UUID.randomUUID()));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("notifyRecurringTicketAlert_nullTicket_doesNothing")
    void notifyRecurringTicketAlert_nullTicket_doesNothing() {
        notificationService.notifyRecurringTicketAlert(null, 3);

        verifyNoInteractions(userRepository);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendStaffRequestToManager_success_withoutTicket")
    void sendStaffRequestToManager_success_withoutTicket() {
        mockCurrentUser(staff);
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId())).thenReturn(List.of(department));
        when(departmentRepository.findById(department.getDepartmentId())).thenReturn(Optional.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any())).thenReturn(List.of(manager1, manager2));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationMapper.toResponse(any(UserNotification.class))).thenAnswer(inv -> {
            UserNotification un = inv.getArgument(0);
            return NotificationResponse.builder()
                    .notificationId(un.getNotification().getNotificationId())
                    .userId(un.getUser().getUserId())
                    .title(un.getNotification().getTitle())
                    .content(un.getNotification().getContent())
                    .build();
        });

        CreateStaffRequest request = CreateStaffRequest.builder()
                .title("Xin cấp thêm máy bơm")
                .content("Cần máy bơm công suất lớn")
                .build();

        StaffRequestResponse response = notificationService.sendStaffRequestToManager(request);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).contains("[Yêu cầu từ cán bộ]");
        assertThat(response.getTitle()).contains("Xin cấp thêm máy bơm");
        assertThat(response.getContent()).contains("Cán bộ gửi: Nhân Viên Xử Lý");
        assertThat(response.getContent()).contains("Phòng Quản Lý Đô Thị");
        assertThat(response.getContent()).contains("Cần máy bơm công suất lớn");
        assertThat(response.getRecipientManagerIds()).containsExactlyInAnyOrder(manager1.getUserId(), manager2.getUserId());
        assertThat(response.getRecipientManagerNames()).containsExactlyInAnyOrder(manager1.getFullName(), manager2.getFullName());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getTicket()).isNull();
        assertThat(saved.getRecipients()).hasSize(2);

        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/" + manager1.getUserId()), any(NotificationResponse.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/" + manager2.getUserId()), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("sendStaffRequestToManager_success_withTicket_autoDetectsStep")
    void sendStaffRequestToManager_success_withTicket_autoDetectsStep() {
        mockCurrentUser(staff);
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId())).thenReturn(List.of(department));
        when(departmentRepository.findById(department.getDepartmentId())).thenReturn(Optional.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any())).thenReturn(List.of(manager1));
        when(ticketRepository.findById(ticket.getTicketId())).thenReturn(Optional.of(ticket));
        when(ticketStepRepository.findByTicketTicketId(ticket.getTicketId())).thenReturn(List.of(ticketStep));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateStaffRequest request = CreateStaffRequest.builder()
                .ticketId(ticket.getTicketId())
                .title("Báo cáo sự cố khẩn")
                .content("Cần tăng cường nhân lực tại hiện trường")
                .build();

        StaffRequestResponse response = notificationService.sendStaffRequestToManager(request);

        assertThat(response).isNotNull();
        assertThat(response.getTicketId()).isEqualTo(ticket.getTicketId());
        assertThat(response.getStepId()).isEqualTo(ticketStep.getId());
        assertThat(response.getStepName()).isEqualTo("Khảo sát hiện trường");
        assertThat(response.getRecipientManagerIds()).containsExactly(manager1.getUserId());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getTicket()).isEqualTo(ticket);
    }

    @Test
    @DisplayName("sendStaffRequestToManager_success_withExplicitStepId")
    void sendStaffRequestToManager_success_withExplicitStepId() {
        mockCurrentUser(staff);
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId())).thenReturn(List.of(department));
        when(departmentRepository.findById(department.getDepartmentId())).thenReturn(Optional.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any())).thenReturn(List.of(manager1));
        when(ticketStepRepository.findById(ticketStep.getId())).thenReturn(Optional.of(ticketStep));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateStaffRequest request = CreateStaffRequest.builder()
                .ticketId(ticket.getTicketId())
                .stepId(ticketStep.getId())
                .title("Xin gia hạn hoàn thành bước")
                .content("Do thời tiết mưa bão, xin gia hạn thêm 1 ngày")
                .build();

        StaffRequestResponse response = notificationService.sendStaffRequestToManager(request);

        assertThat(response).isNotNull();
        assertThat(response.getTicketId()).isEqualTo(ticket.getTicketId());
        assertThat(response.getStepId()).isEqualTo(ticketStep.getId());
        assertThat(response.getStepName()).isEqualTo("Khảo sát hiện trường");
        assertThat(response.getContent()).contains("Bước xử lý: [Khảo sát hiện trường]");
    }

    @Test
    @DisplayName("sendStaffRequestToManager_stepIdNotMatchingTicketId_throwsInvalidRequest")
    void sendStaffRequestToManager_stepIdNotMatchingTicketId_throwsInvalidRequest() {
        mockCurrentUser(staff);
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId())).thenReturn(List.of(department));
        when(departmentRepository.findById(department.getDepartmentId())).thenReturn(Optional.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any())).thenReturn(List.of(manager1));
        when(ticketStepRepository.findById(ticketStep.getId())).thenReturn(Optional.of(ticketStep));

        UUID anotherTicketId = UUID.randomUUID();
        CreateStaffRequest request = CreateStaffRequest.builder()
                .ticketId(anotherTicketId)
                .stepId(ticketStep.getId())
                .title("Yêu cầu sai khớp vé")
                .content("Nội dung")
                .build();

        assertThatThrownBy(() -> notificationService.sendStaffRequestToManager(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("sendStaffRequestToManager_stepNotAssignedToStaffOrDept_throwsForbidden")
    void sendStaffRequestToManager_stepNotAssignedToStaffOrDept_throwsForbidden() {
        mockCurrentUser(staff);
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId())).thenReturn(List.of(department));
        when(departmentRepository.findById(department.getDepartmentId())).thenReturn(Optional.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any())).thenReturn(List.of(manager1));

        Department otherDept = Department.builder().departmentId(UUID.randomUUID()).name("Phòng Khác").build();
        User otherStaff = User.builder().userId(UUID.randomUUID()).department(otherDept).build();
        TicketStep otherStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .step(stepDef)
                .assignee(otherStaff)
                .build();
        when(ticketStepRepository.findById(otherStep.getId())).thenReturn(Optional.of(otherStep));

        CreateStaffRequest request = CreateStaffRequest.builder()
                .stepId(otherStep.getId())
                .title("Can thiệp bước của người khác")
                .content("Nội dung")
                .build();

        assertThatThrownBy(() -> notificationService.sendStaffRequestToManager(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("sendStaffRequestToManager_ticketNotAssignedToStaffOrDept_throwsForbidden")
    void sendStaffRequestToManager_ticketNotAssignedToStaffOrDept_throwsForbidden() {
        mockCurrentUser(staff);
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId())).thenReturn(List.of(department));
        when(departmentRepository.findById(department.getDepartmentId())).thenReturn(Optional.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any())).thenReturn(List.of(manager1));
        when(ticketRepository.findById(ticket.getTicketId())).thenReturn(Optional.of(ticket));
        when(ticketStepRepository.findByTicketTicketId(ticket.getTicketId())).thenReturn(Collections.emptyList());

        CreateStaffRequest request = CreateStaffRequest.builder()
                .ticketId(ticket.getTicketId())
                .title("Vé không thuộc thẩm quyền")
                .content("Nội dung")
                .build();

        assertThatThrownBy(() -> notificationService.sendStaffRequestToManager(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("sendStaffRequestToManager_staleDirectDept_stillSucceedsWithUserDepartments")
    void sendStaffRequestToManager_staleDirectDept_stillSucceedsWithUserDepartments() {
        UUID staleDeptId = UUID.randomUUID();
        User staffWithStaleDept = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Cán bộ phòng ban lệch")
                .email("stale@cityfix.com")
                .role(new Role(UUID.randomUUID(), "STAFF"))
                .department(Department.builder().departmentId(staleDeptId).build())
                .status(UserStatus.ACTIVE)
                .build();

        mockCurrentUser(staffWithStaleDept);
        when(userDepartmentRepository.findDepartmentsByUserId(staffWithStaleDept.getUserId())).thenReturn(List.of(department));
        when(departmentRepository.findById(staleDeptId)).thenReturn(Optional.empty());
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any())).thenReturn(List.of(manager1));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateStaffRequest request = CreateStaffRequest.builder()
                .title("Yêu cầu khi direct dept stale")
                .content("Vẫn thành công nhờ user_department")
                .build();

        StaffRequestResponse response = notificationService.sendStaffRequestToManager(request);

        assertThat(response).isNotNull();
        assertThat(response.getRecipientManagerIds()).containsExactly(manager1.getUserId());
    }

    @Test
    @DisplayName("sendStaffRequestToManager_multipleDepartments_sendsToManagersOfAllDepartments")
    void sendStaffRequestToManager_multipleDepartments_sendsToManagersOfAllDepartments() {
        Department dept2 = Department.builder()
                .departmentId(UUID.randomUUID())
                .name("Phòng Môi trường")
                .build();
        User managerOfDept2 = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Quản lý Môi trường")
                .email("mgr_env@cityfix.com")
                .role(new Role(UUID.randomUUID(), "MANAGER"))
                .status(UserStatus.ACTIVE)
                .build();

        mockCurrentUser(staff);
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId())).thenReturn(List.of(department, dept2));
        when(departmentRepository.findById(department.getDepartmentId())).thenReturn(Optional.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any())).thenReturn(List.of(manager1, managerOfDept2));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateStaffRequest request = CreateStaffRequest.builder()
                .title("Yêu cầu nhân viên đa phòng ban")
                .content("Gửi cho cả 2 quản lý của 2 phòng ban")
                .build();

        StaffRequestResponse response = notificationService.sendStaffRequestToManager(request);

        assertThat(response).isNotNull();
        assertThat(response.getRecipientManagerIds()).containsExactlyInAnyOrder(manager1.getUserId(), managerOfDept2.getUserId());
    }

    @Test
    @DisplayName("sendStaffRequestToManager_ticketNotFound_throwsException")
    void sendStaffRequestToManager_ticketNotFound_throwsException() {
        mockCurrentUser(staff);
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId())).thenReturn(List.of(department));
        when(departmentRepository.findById(department.getDepartmentId())).thenReturn(Optional.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any())).thenReturn(List.of(manager1));
        UUID notFoundTicketId = UUID.randomUUID();
        when(ticketRepository.findById(notFoundTicketId)).thenReturn(Optional.empty());

        CreateStaffRequest request = CreateStaffRequest.builder()
                .ticketId(notFoundTicketId)
                .title("Yêu cầu với vé không tồn tại")
                .content("Nội dung")
                .build();

        assertThatThrownBy(() -> notificationService.sendStaffRequestToManager(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("sendStaffRequestToManager_staffNotInDepartment_throwsException")
    void sendStaffRequestToManager_staffNotInDepartment_throwsException() {
        Role staffRole = new Role(UUID.randomUUID(), "STAFF");
        User staffWithoutDept = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Nhân Viên Chưa Có Phòng")
                .email("nodept@cityfix.com")
                .role(staffRole)
                .status(UserStatus.ACTIVE)
                .build();

        mockCurrentUser(staffWithoutDept);
        when(userDepartmentRepository.findDepartmentsByUserId(staffWithoutDept.getUserId())).thenReturn(Collections.emptyList());

        CreateStaffRequest request = CreateStaffRequest.builder()
                .title("Yêu cầu khi chưa có phòng")
                .content("Nội dung")
                .build();

        assertThatThrownBy(() -> notificationService.sendStaffRequestToManager(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_IN_DEPARTMENT);
    }

    @Test
    @DisplayName("sendStaffRequestToManager_noActiveManagers_throwsException")
    void sendStaffRequestToManager_noActiveManagers_throwsException() {
        mockCurrentUser(staff);
        when(userDepartmentRepository.findDepartmentsByUserId(staff.getUserId())).thenReturn(List.of(department));
        when(departmentRepository.findById(department.getDepartmentId())).thenReturn(Optional.of(department));
        when(userDepartmentRepository.findActiveManagersByDepartmentIds(any())).thenReturn(Collections.emptyList());
        when(userRepository.findActiveManagersByDepartmentIds(any())).thenReturn(Collections.emptyList());

        CreateStaffRequest request = CreateStaffRequest.builder()
                .title("Yêu cầu khi không có quản lý")
                .content("Nội dung")
                .build();

        assertThatThrownBy(() -> notificationService.sendStaffRequestToManager(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MANAGER_NOT_FOUND);
    }

    @Test
    @DisplayName("sendStaffRequestToManager_userNotStaff_throwsException")
    void sendStaffRequestToManager_userNotStaff_throwsException() {
        mockCurrentUser(reporter);

        CreateStaffRequest request = CreateStaffRequest.builder()
                .title("Yêu cầu từ citizen")
                .content("Nội dung")
                .build();

        assertThatThrownBy(() -> notificationService.sendStaffRequestToManager(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(notificationRepository, never()).save(any());
    }
}