package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.NotificationMapper;
import com.cityfix.backend.model.dto.response.NotificationResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Notification;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.DepartmentRepository;
import com.cityfix.backend.repository.NotificationRepository;
import com.cityfix.backend.repository.TicketVoteRepository;
import com.cityfix.backend.repository.UserDepartmentRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cityfix.backend.util.SecurityUtils;

import java.util.ArrayList;
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
    private final TicketVoteRepository ticketVoteRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;

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
                        if (dept.getManager() != null && dept.getManager().getUserId() != null) {
                            managers.put(dept.getManager().getUserId(), dept.getManager());
                        }
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
            if (dept.getManager() != null && dept.getManager().getUserId() != null) {
                managers.put(dept.getManager().getUserId(), dept.getManager());
            }
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

    private void saveAndSendNotification(User recipient, String title, String content) {
        UUID recipientId = recipient.getUserId();
        Notification notification = Notification.builder()
                .user(recipient)
                .title(title)
                .content(content)
                .isRead(false)
                .build();
        notification = notificationRepository.save(notification);

        try {
            NotificationResponse response = notificationMapper.toResponse(notification);
            if (response != null && response.getUserId() == null) {
                response.setUserId(recipientId);
            }
            messagingTemplate.convertAndSend("/topic/notifications/" + recipientId, response);
        } catch (Exception e) {
            log.error("Lỗi khi gửi WebSocket notification cho user {}: {}", recipientId, e.getMessage());
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

        Set<UUID> notifiedUserIds = new HashSet<>();

        // 1. Người báo cáo (Reporter)
        User reporter = ticket.getUser();
        if (isEligibleRecipient(reporter, actorId) && notifiedUserIds.add(reporter.getUserId())) {
            String title = "Tiến độ sự cố: Bắt đầu bước xử lý";
            String content = "Sự cố của bạn đang được nhân viên " + staffName + " thực hiện tại bước '" + stepName + "'.";
            saveAndSendNotification(reporter, title, content);
        }

        // 2. Người upvote (Upvoters)
        if (ticket.getTicketId() != null) {
            List<User> upvoters = ticketVoteRepository.findUpvotersByTicketId(ticket.getTicketId());
            if (upvoters != null) {
                String title = "Tiến độ sự cố: Bắt đầu bước xử lý";
                String content = "Sự cố bạn theo dõi đang được nhân viên " + staffName + " thực hiện tại bước '" + stepName + "'.";
                for (User upvoter : upvoters) {
                    if (isEligibleRecipient(upvoter, actorId) && notifiedUserIds.add(upvoter.getUserId())) {
                        saveAndSendNotification(upvoter, title, content);
                    }
                }
            }
        }

        // 3. Manager cùng phòng ban với Staff
        List<User> managers = resolveDepartmentManagers(effectiveStaff);
        if (!managers.isEmpty()) {
            String title = "Tiến độ xử lý của nhân viên: Bắt đầu bước xử lý";
            String content = "Nhân viên " + staffName + " đã bắt đầu thực hiện bước '" + stepName + "'"
                    + (address.isEmpty() ? "." : " (Sự cố tại: " + address + ").");
            for (User manager : managers) {
                if (isEligibleRecipient(manager, actorId) && notifiedUserIds.add(manager.getUserId())) {
                    saveAndSendNotification(manager, title, content);
                }
            }
        }

        log.info("Sent step-started notifications for ticketId={}, stepId={}, actorId={}, recipientCount={}",
                ticket.getTicketId(), step.getId(), actorId, notifiedUserIds.size());
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

        Set<UUID> notifiedUserIds = new HashSet<>();

        // 1. Người báo cáo (Reporter)
        User reporter = ticket.getUser();
        if (isEligibleRecipient(reporter, actorId) && notifiedUserIds.add(reporter.getUserId())) {
            String title = isTicketResolved
                    ? "Sự cố đã được giải quyết"
                    : "Tiến độ sự cố: Hoàn thành bước xử lý";
            String content = isTicketResolved
                    ? "Nhân viên " + staffName + " đã hoàn thành bước '" + stepName + "'. Toàn bộ các bước xử lý của sự cố đã hoàn tất. Vui lòng kiểm tra và đánh giá kết quả."
                    : "Bước '" + stepName + "' trong sự cố của bạn đã được nhân viên " + staffName + " hoàn thành.";
            saveAndSendNotification(reporter, title, content);
        }

        // 2. Người upvote (Upvoters)
        if (ticket.getTicketId() != null) {
            List<User> upvoters = ticketVoteRepository.findUpvotersByTicketId(ticket.getTicketId());
            if (upvoters != null) {
                String title = isTicketResolved
                        ? "Sự cố đã được giải quyết"
                        : "Tiến độ sự cố: Hoàn thành bước xử lý";
                String content = isTicketResolved
                        ? "Nhân viên " + staffName + " đã hoàn thành bước '" + stepName + "'. Sự cố bạn theo dõi đã được xử lý hoàn tất."
                        : "Bước '" + stepName + "' của sự cố bạn theo dõi đã được nhân viên " + staffName + " hoàn thành.";
                for (User upvoter : upvoters) {
                    if (isEligibleRecipient(upvoter, actorId) && notifiedUserIds.add(upvoter.getUserId())) {
                        saveAndSendNotification(upvoter, title, content);
                    }
                }
            }
        }

        // 3. Manager cùng phòng ban với Staff
        List<User> managers = resolveDepartmentManagers(effectiveStaff);
        if (!managers.isEmpty()) {
            String title = isTicketResolved
                    ? "Sự cố đã được giải quyết hoàn tất"
                    : "Tiến độ xử lý của nhân viên: Hoàn thành bước xử lý";
            String content = isTicketResolved
                    ? "Nhân viên " + staffName + " đã hoàn thành bước cuối cùng '" + stepName + "'"
                            + (address.isEmpty() ? "" : " (Sự cố tại: " + address + ")")
                            + ". Toàn bộ sự cố đã chuyển sang trạng thái Đã giải quyết."
                    : "Nhân viên " + staffName + " đã hoàn thành bước '" + stepName + "'"
                            + (address.isEmpty() ? "." : " (Sự cố tại: " + address + ").");
            for (User manager : managers) {
                if (isEligibleRecipient(manager, actorId) && notifiedUserIds.add(manager.getUserId())) {
                    saveAndSendNotification(manager, title, content);
                }
            }
        }

        log.info("Sent step-completed notifications for ticketId={}, stepId={}, actorId={}, isTicketResolved={}, recipientCount={}",
                ticket.getTicketId(), step.getId(), actorId, isTicketResolved, notifiedUserIds.size());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Boolean isRead, Pageable pageable) {
        User currentUser = getCurrentUser();
        Pageable effectivePageable = (pageable == null || pageable.isUnpaged())
                ? Pageable.unpaged()
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.unsorted());

        Page<Notification> notifications;
        if (isRead != null) {
            notifications = notificationRepository.findByUser_UserIdAndIsReadOrderByCreatedAtDesc(
                    currentUser.getUserId(), isRead, effectivePageable);
        } else {
            notifications = notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(
                    currentUser.getUserId(), effectivePageable);
        }
        return notifications.map(notificationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        User currentUser = getCurrentUser();
        return notificationRepository.countByUser_UserIdAndIsReadFalse(currentUser.getUserId());
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        User currentUser = getCurrentUser();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (notification.getUser() == null
                || notification.getUser().getUserId() == null
                || !notification.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }
        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead() {
        User currentUser = getCurrentUser();
        notificationRepository.markAllAsReadByUserId(currentUser.getUserId());
    }
}
