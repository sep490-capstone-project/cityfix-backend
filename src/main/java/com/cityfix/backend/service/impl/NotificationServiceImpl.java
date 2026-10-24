package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.NotificationMapper;
import com.cityfix.backend.model.dto.request.CreateStaffRequest;
import com.cityfix.backend.model.dto.response.NotificationResponse;
import com.cityfix.backend.model.dto.response.StaffRequestResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Notification;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.UserNotification;
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
import com.cityfix.backend.service.NotificationService;
import com.cityfix.backend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final TicketVoteRepository ticketVoteRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final TicketStepRepository ticketStepRepository;
    private final TicketRepository ticketRepository;

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        if (principal instanceof User user && user.getUserId() != null) {
            return user;
        }

        String email = SecurityUtils.getCurrentUserEmail();
        return userRepository.findByEmail(email)
                .or(SecurityUtils::getCurrentUserPrincipal)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private boolean isEligibleRecipient(User user, UUID actorId) {
        if (user == null || user.getUserId() == null) {
            return false;
        }
        if (actorId != null && actorId.equals(user.getUserId())) {
            return false;
        }
        if (user.isDeleted()) {
            return false;
        }
        return user.getStatus() == null || user.getStatus() == UserStatus.ACTIVE;
    }

    private UUID extractDepartmentId(Department dept) {
        if (dept == null) {
            return null;
        }
        if (dept instanceof org.hibernate.proxy.HibernateProxy proxy) {
            Object id = proxy.getHibernateLazyInitializer().getIdentifier();
            if (id instanceof UUID uuid) {
                return uuid;
            }
        }
        return dept.getDepartmentId();
    }

    private List<User> resolveDepartmentManagers(User staff) {
        if (staff == null) {
            return Collections.emptyList();
        }

        Set<UUID> departmentIds = new LinkedHashSet<>();
        Map<UUID, User> managers = new LinkedHashMap<>();

        if (staff.getUserId() != null) {
            List<Department> staffDepts = userDepartmentRepository.findDepartmentsByUserId(staff.getUserId());
            if (staffDepts != null) {
                for (Department dept : staffDepts) {
                    if (dept != null && !dept.isDeleted() && dept.getDepartmentId() != null) {
                        departmentIds.add(dept.getDepartmentId());
                    }
                }
            }
        }

        UUID directDeptId = extractDepartmentId(staff.getDepartment());
        if (directDeptId != null) {
            Department dept = departmentRepository.findById(directDeptId)
                    .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));
            if (Boolean.TRUE.equals(dept.isDeleted())) {
                return Collections.emptyList();
            }
            departmentIds.add(dept.getDepartmentId());
        }

        if (!departmentIds.isEmpty()) {
            List<User> udManagers = userDepartmentRepository.findActiveManagersByDepartmentIds(departmentIds);
            if (udManagers != null) {
                for (User mgr : udManagers) {
                    if (mgr != null && mgr.getUserId() != null) {
                        managers.put(mgr.getUserId(), mgr);
                    }
                }
            }

            List<User> directManagers = userRepository.findActiveManagersByDepartmentIds(departmentIds);
            if (directManagers != null) {
                for (User mgr : directManagers) {
                    if (mgr != null && mgr.getUserId() != null) {
                        managers.put(mgr.getUserId(), mgr);
                    }
                }
            }
        }

        return new ArrayList<>(managers.values());
    }

    @Override
    @Transactional
    public void createAndSendNotification(Collection<User> recipients, String title, String content) {
        createAndSendNotification(null, recipients, title, content);
    }

    @Override
    @Transactional
    public void createAndSendNotification(Ticket ticket, Collection<User> recipients, String title, String content) {
        if (recipients == null || recipients.isEmpty()) {
            log.warn("createAndSendNotification called with empty or null recipients. ticketId={}, title={}",
                    ticket != null ? ticket.getTicketId() : null, title);
            return;
        }

        Notification notification = Notification.builder()
                .ticket(ticket)
                .title(title)
                .content(content)
                .build();

        Set<UUID> addedUserIds = new HashSet<>();
        for (User recipient : recipients) {
            if (isEligibleRecipient(recipient, null) && addedUserIds.add(recipient.getUserId())) {
                notification.addRecipient(recipient);
            }
        }

        if (notification.getRecipients().isEmpty()) {
            log.warn("No eligible active recipients found for notification. ticketId={}, title={}",
                    ticket != null ? ticket.getTicketId() : null, title);
            return;
        }

        Notification saved = notificationRepository.save(notification);
        Notification target = saved != null ? saved : notification;

        for (UserNotification un : target.getRecipients()) {
            try {
                NotificationResponse response = notificationMapper.toResponse(un);
                messagingTemplate.convertAndSend("/topic/notifications/" + un.getUser().getUserId(), response);
            } catch (Exception e) {
                log.error("Lỗi khi gửi WebSocket notification cho user {}: {}", un.getUser().getUserId(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void notifyStepStarted(TicketStep step, User staff) {
        if (step == null || step.getTicket() == null) {
            return;
        }

        Ticket ticket = step.getTicket();
        User effectiveStaff = staff != null ? staff : step.getAssignee();
        UUID actorId = effectiveStaff != null ? effectiveStaff.getUserId() : null;

        String stepName = (step.getStep() != null && step.getStep().getStepName() != null && !step.getStep().getStepName().isBlank())
                ? step.getStep().getStepName()
                : "Bước xử lý";
        String staffName = (effectiveStaff != null && effectiveStaff.getFullName() != null && !effectiveStaff.getFullName().isBlank())
                ? effectiveStaff.getFullName()
                : "Cán bộ xử lý";
        String address = (ticket.getAddress() != null && !ticket.getAddress().isBlank())
                ? ticket.getAddress()
                : "";

        Set<UUID> notifiedCitizenIds = new HashSet<>();
        List<User> citizenRecipients = new ArrayList<>();

        // 1. Người báo cáo (Reporter)
        User reporter = ticket.getUser();
        if (isEligibleRecipient(reporter, actorId) && notifiedCitizenIds.add(reporter.getUserId())) {
            citizenRecipients.add(reporter);
        }

        // 2. Người upvote (Upvoters)
        if (ticket.getTicketId() != null) {
            List<User> upvoters = ticketVoteRepository.findUpvotersByTicketId(ticket.getTicketId());
            if (upvoters != null) {
                for (User upvoter : upvoters) {
                    if (isEligibleRecipient(upvoter, actorId) && notifiedCitizenIds.add(upvoter.getUserId())) {
                        citizenRecipients.add(upvoter);
                    }
                }
            }
        }

        if (!citizenRecipients.isEmpty()) {
            String title = "Tiến độ sự cố: Bắt đầu bước xử lý";
            String content = "Sự cố của bạn đang được nhân viên " + staffName + " thực hiện tại bước '" + stepName + "'.";
            createAndSendNotification(ticket, citizenRecipients, title, content);
        }

        // 3. Manager cùng phòng ban với Staff
        List<User> managers = resolveDepartmentManagers(effectiveStaff);
        List<User> managerRecipients = new ArrayList<>();
        Set<UUID> notifiedManagerIds = new HashSet<>();
        for (User manager : managers) {
            if (isEligibleRecipient(manager, actorId) && notifiedManagerIds.add(manager.getUserId())) {
                managerRecipients.add(manager);
            }
        }

        if (!managerRecipients.isEmpty()) {
            String title = "Tiến độ xử lý của nhân viên: Bắt đầu bước xử lý";
            String content = "Nhân viên " + staffName + " đã bắt đầu thực hiện bước '" + stepName + "'"
                    + (address.isEmpty() ? "." : " (Sự cố tại: " + address + ").");
            createAndSendNotification(ticket, managerRecipients, title, content);
        }

        log.info("Sent step-started notifications for ticketId={}, stepId={}, actorId={}, citizenCount={}, managerCount={}",
                ticket.getTicketId(), step.getId(), actorId, citizenRecipients.size(), managerRecipients.size());
    }

    @Override
    @Transactional
    public void notifyStepCompleted(TicketStep step, User staff, boolean isTicketResolved) {
        if (step == null || step.getTicket() == null) {
            return;
        }

        Ticket ticket = step.getTicket();
        User effectiveStaff = staff != null ? staff : step.getAssignee();
        UUID actorId = effectiveStaff != null ? effectiveStaff.getUserId() : null;

        String stepName = (step.getStep() != null && step.getStep().getStepName() != null && !step.getStep().getStepName().isBlank())
                ? step.getStep().getStepName()
                : "Bước xử lý";
        String staffName = (effectiveStaff != null && effectiveStaff.getFullName() != null && !effectiveStaff.getFullName().isBlank())
                ? effectiveStaff.getFullName()
                : "Cán bộ xử lý";
        String address = (ticket.getAddress() != null && !ticket.getAddress().isBlank())
                ? ticket.getAddress()
                : "";

        Set<UUID> notifiedCitizenIds = new HashSet<>();
        List<User> citizenRecipients = new ArrayList<>();

        // 1. Người báo cáo (Reporter)
        User reporter = ticket.getUser();
        if (isEligibleRecipient(reporter, actorId) && notifiedCitizenIds.add(reporter.getUserId())) {
            citizenRecipients.add(reporter);
        }

        // 2. Người upvote (Upvoters)
        if (ticket.getTicketId() != null) {
            List<User> upvoters = ticketVoteRepository.findUpvotersByTicketId(ticket.getTicketId());
            if (upvoters != null) {
                for (User upvoter : upvoters) {
                    if (isEligibleRecipient(upvoter, actorId) && notifiedCitizenIds.add(upvoter.getUserId())) {
                        citizenRecipients.add(upvoter);
                    }
                }
            }
        }

        if (!citizenRecipients.isEmpty()) {
            String title = isTicketResolved
                    ? "Sự cố đã được giải quyết"
                    : "Tiến độ sự cố: Hoàn thành bước xử lý";
            String content = isTicketResolved
                    ? "Nhân viên " + staffName + " đã hoàn thành bước '" + stepName + "'. Toàn bộ các bước xử lý của sự cố đã hoàn tất. Vui lòng kiểm tra và đánh giá kết quả."
                    : "Bước '" + stepName + "' trong sự cố của bạn đã được nhân viên " + staffName + " hoàn thành.";
            createAndSendNotification(ticket, citizenRecipients, title, content);
        }

        // 3. Manager cùng phòng ban với Staff
        List<User> managers = resolveDepartmentManagers(effectiveStaff);
        List<User> managerRecipients = new ArrayList<>();
        Set<UUID> notifiedManagerIds = new HashSet<>();
        for (User manager : managers) {
            if (isEligibleRecipient(manager, actorId) && notifiedManagerIds.add(manager.getUserId())) {
                managerRecipients.add(manager);
            }
        }

        if (!managerRecipients.isEmpty()) {
            String title = isTicketResolved
                    ? "Sự cố đã được giải quyết hoàn tất"
                    : "Tiến độ xử lý của nhân viên: Hoàn thành bước xử lý";
            String content = isTicketResolved
                    ? "Nhân viên " + staffName + " đã hoàn thành bước cuối cùng '" + stepName + "'"
                            + (address.isEmpty() ? "" : " (Sự cố tại: " + address + ")")
                            + ". Toàn bộ sự cố đã chuyển sang trạng thái Đã giải quyết."
                    : "Nhân viên " + staffName + " đã hoàn thành bước '" + stepName + "'"
                            + (address.isEmpty() ? "." : " (Sự cố tại: " + address + ").");
            createAndSendNotification(ticket, managerRecipients, title, content);
        }

        log.info("Sent step-completed notifications for ticketId={}, stepId={}, actorId={}, isTicketResolved={}, citizenCount={}, managerCount={}",
                ticket.getTicketId(), step.getId(), actorId, isTicketResolved, citizenRecipients.size(), managerRecipients.size());
    }

    private List<User> resolveManagersForRecurringTickets(Collection<UUID> recurringTicketIds) {
        if (recurringTicketIds == null || recurringTicketIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, User> targetManagers = new LinkedHashMap<>();

        // 1. Tìm các Manager từ lịch sử duyệt/xử lý của các vé cũ (TicketHistory)
        List<User> historyManagers = ticketHistoryRepository.findActiveManagersByTicketIds(recurringTicketIds);
        if (historyManagers != null) {
            for (User mgr : historyManagers) {
                if (mgr != null && mgr.getUserId() != null) {
                    targetManagers.put(mgr.getUserId(), mgr);
                }
            }
        }

        // 2. Nếu chưa có, tìm Manager của các phòng ban phụ trách các bước (TicketStep) của các vé cũ
        if (targetManagers.isEmpty()) {
            List<TicketStep> steps = ticketStepRepository.findByTicketTicketIdIn(recurringTicketIds);
            if (steps != null && !steps.isEmpty()) {
                Set<UUID> departmentIds = new LinkedHashSet<>();
                for (TicketStep s : steps) {
                    if (s != null && !s.isDeleted() && s.getAssignee() != null) {
                        UUID deptId = extractDepartmentId(s.getAssignee().getDepartment());
                        if (deptId != null) {
                            departmentIds.add(deptId);
                        }
                    }
                }
                if (!departmentIds.isEmpty()) {
                    List<User> deptManagers = userRepository.findActiveManagersByDepartmentIds(departmentIds);
                    if (deptManagers != null) {
                        for (User mgr : deptManagers) {
                            if (mgr != null && mgr.getUserId() != null) {
                                targetManagers.put(mgr.getUserId(), mgr);
                            }
                        }
                    }
                    List<User> udManagers = userDepartmentRepository.findActiveManagersByDepartmentIds(departmentIds);
                    if (udManagers != null) {
                        for (User mgr : udManagers) {
                            if (mgr != null && mgr.getUserId() != null) {
                                targetManagers.put(mgr.getUserId(), mgr);
                            }
                        }
                    }
                }
            }
        }

        return new ArrayList<>(targetManagers.values());
    }

    @Override
    @Transactional
    public void notifyRecurringTicketAlert(Ticket ticket, long occurrenceCount, Collection<UUID> recurringTicketIds) {
        if (ticket == null) {
            return;
        }

        List<User> targetManagers = resolveManagersForRecurringTickets(recurringTicketIds);
        if (targetManagers == null || targetManagers.isEmpty()) {
            targetManagers = userRepository.findActiveManagers();
        }

        if (targetManagers == null || targetManagers.isEmpty()) {
            log.warn("No active managers found for recurring ticket alert. ticketId={}", ticket.getTicketId());
            return;
        }

        String address = (ticket.getAddress() != null && !ticket.getAddress().isBlank())
                ? ticket.getAddress()
                : "vị trí đã báo";

        String title = "[Cảnh báo] Sự cố tái diễn (Lần thứ " + occurrenceCount + ")";
        String content = "Sự cố tại địa chỉ \"" + address + "\" đã xảy ra lần thứ " + occurrenceCount
                + " trong vòng 30 ngày qua dù trước đó đã hoàn tất xử lý. Vui lòng kiểm tra chất lượng giải quyết của các đơn vị phụ trách.";

        createAndSendNotification(ticket, targetManagers, title, content);

        log.info("Sent recurring ticket alert to {} targeted managers for ticketId={}, occurrenceCount={}",
                targetManagers.size(), ticket.getTicketId(), occurrenceCount);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Boolean isRead, Pageable pageable) {
        User currentUser = getCurrentUser();
        Pageable effectivePageable = (pageable == null || pageable.isUnpaged())
                ? Pageable.unpaged()
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.unsorted());

        Page<UserNotification> userNotifications;
        if (isRead != null) {
            userNotifications = userNotificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(
                    currentUser.getUserId(), isRead, effectivePageable);
        } else {
            userNotifications = userNotificationRepository.findByUserIdOrderByCreatedAtDesc(
                    currentUser.getUserId(), effectivePageable);
        }
        return userNotifications.map(notificationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        User currentUser = getCurrentUser();
        return userNotificationRepository.countByUser_UserIdAndIsReadFalse(currentUser.getUserId());
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        User currentUser = getCurrentUser();
        UserNotification userNotification = userNotificationRepository.findByNotificationIdAndUserId(notificationId, currentUser.getUserId())
                .orElse(null);

        if (userNotification == null) {
            if (notificationRepository.existsById(notificationId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        if (!userNotification.isRead()) {
            userNotification.setRead(true);
            userNotification.setReadAt(ZonedDateTime.now());
            userNotification = userNotificationRepository.save(userNotification);
        }
        return notificationMapper.toResponse(userNotification);
    }

    @Override
    @Transactional
    public void markAllAsRead() {
        User currentUser = getCurrentUser();
        userNotificationRepository.markAllAsReadByUserId(currentUser.getUserId());
    }

    @Override
    @Transactional
    public StaffRequestResponse sendStaffRequestToManager(CreateStaffRequest request) {
        User staff = getCurrentUser();

        if (staff.getRole() == null || !"STAFF".equalsIgnoreCase(staff.getRole().getRoleName())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        Set<UUID> departmentIds = new LinkedHashSet<>();
        List<String> departmentNames = new ArrayList<>();

        if (staff.getUserId() != null) {
            List<Department> staffDepts = userDepartmentRepository.findDepartmentsByUserId(staff.getUserId());
            if (staffDepts != null) {
                for (Department dept : staffDepts) {
                    if (dept != null && !dept.isDeleted() && dept.getDepartmentId() != null) {
                        departmentIds.add(dept.getDepartmentId());
                        if (dept.getName() != null && !departmentNames.contains(dept.getName())) {
                            departmentNames.add(dept.getName());
                        }
                    }
                }
            }
        }

        UUID directDeptId = extractDepartmentId(staff.getDepartment());
        if (directDeptId != null) {
            Department dept = departmentRepository.findById(directDeptId)
                    .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));
            if (!Boolean.TRUE.equals(dept.isDeleted())) {
                departmentIds.add(dept.getDepartmentId());
                if (dept.getName() != null && !departmentNames.contains(dept.getName())) {
                    departmentNames.add(dept.getName());
                }
            }
        }

        if (departmentIds.isEmpty()) {
            throw new AppException(ErrorCode.USER_NOT_IN_DEPARTMENT);
        }

        List<User> managers = resolveDepartmentManagers(staff);
        List<User> eligibleManagers = new ArrayList<>();
        Set<UUID> addedManagerIds = new HashSet<>();

        for (User mgr : managers) {
            if (isEligibleRecipient(mgr, staff.getUserId()) && addedManagerIds.add(mgr.getUserId())) {
                eligibleManagers.add(mgr);
            }
        }

        if (eligibleManagers.isEmpty()) {
            throw new AppException(ErrorCode.MANAGER_NOT_FOUND);
        }

        Ticket ticket = null;
        if (request.getTicketId() != null) {
            ticket = ticketRepository.findById(request.getTicketId())
                    .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));
        }

        String title = request.getTitle() != null ? request.getTitle().trim() : "";
        String formattedTitle = title.startsWith("[") ? title : "[Yêu cầu từ cán bộ] " + title;

        String deptInfo = !departmentNames.isEmpty() ? String.join(", ", departmentNames) : "Không xác định";
        String senderInfo = "Cán bộ gửi: " + (staff.getFullName() != null ? staff.getFullName() : "Cán bộ")
                + (staff.getEmail() != null ? " (" + staff.getEmail() + ")" : "") + "\n"
                + "Phòng ban: " + deptInfo + "\n";
        String formattedContent = senderInfo + "Nội dung: " + (request.getContent() != null ? request.getContent().trim() : "");

        Notification notification = Notification.builder()
                .ticket(ticket)
                .title(formattedTitle)
                .content(formattedContent)
                .build();

        for (User manager : eligibleManagers) {
            notification.addRecipient(manager);
        }

        Notification saved = notificationRepository.save(notification);
        Notification target = saved != null ? saved : notification;

        for (UserNotification un : target.getRecipients()) {
            try {
                NotificationResponse notifResponse = notificationMapper.toResponse(un);
                messagingTemplate.convertAndSend("/topic/notifications/" + un.getUser().getUserId(), notifResponse);
            } catch (Exception e) {
                log.error("Lỗi khi gửi WebSocket notification cho manager {}: {}", un.getUser().getUserId(), e.getMessage());
            }
        }

        log.info("Staff {} sent request to {} managers. notificationId={}",
                staff.getUserId(), eligibleManagers.size(), target.getNotificationId());

        return StaffRequestResponse.builder()
                .notificationId(target.getNotificationId())
                .ticketId(ticket != null ? ticket.getTicketId() : null)
                .title(target.getTitle())
                .content(target.getContent())
                .recipientManagerIds(eligibleManagers.stream().map(User::getUserId).toList())
                .recipientManagerNames(eligibleManagers.stream().map(User::getFullName).toList())
                .createdAt(target.getCreatedAt() != null ? target.getCreatedAt() : ZonedDateTime.now())
                .build();
    }
}
