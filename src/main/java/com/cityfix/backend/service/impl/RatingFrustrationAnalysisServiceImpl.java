package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.response.RatingFrustrationAnalysisResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.service.NotificationService;
import com.cityfix.backend.service.RatingFrustrationAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingFrustrationAnalysisServiceImpl implements RatingFrustrationAnalysisService {

    private final TicketRepository ticketRepository;
    private final RatingRepository ratingRepository;
    private final TicketVoteRepository ticketVoteRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public RatingFrustrationAnalysisResponse analyzeFrustration(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        List<Rating> ratings = ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId);
        List<User> upvoters = ticketVoteRepository.findUpvotersByTicketId(ticketId);

        // 1. Tập người theo dõi = {Creator} U {Upvoters} (khử trùng lặp)
        Set<UUID> followerIds = new HashSet<>();
        if (ticket.getUser() != null && ticket.getUser().getUserId() != null) {
            followerIds.add(ticket.getUser().getUserId());
        }
        if (upvoters != null) {
            for (User u : upvoters) {
                if (u != null && u.getUserId() != null) {
                    followerIds.add(u.getUserId());
                }
            }
        }
        int totalFollowers = Math.max(1, followerIds.size());

        // 2. Thống kê số lượng đánh giá theo từng số sao
        int oneStarCount = 0;
        int twoStarCount = 0;
        int threeStarCount = 0;
        int fourStarCount = 0;
        int fiveStarCount = 0;
        int ratingSum = 0;

        for (Rating r : ratings) {
            if (r.getRate() != null) {
                int rate = r.getRate();
                ratingSum += rate;
                switch (rate) {
                    case 1 -> oneStarCount++;
                    case 2 -> twoStarCount++;
                    case 3 -> threeStarCount++;
                    case 4 -> fourStarCount++;
                    case 5 -> fiveStarCount++;
                    default -> {}
                }
            }
        }

        int totalRatings = ratings.size();
        Double averageRating = totalRatings > 0
                ? Math.round(((double) ratingSum / totalRatings) * 10.0) / 10.0
                : null;

        // 3. Tính điểm phạt theo trọng số bức xúc: 1 sao -> 2đ, 2 sao -> 1đ, 3-5 sao -> 0đ
        int totalPenaltyScore = (oneStarCount * 2) + (twoStarCount * 1);

        // 4. Tính toán các mốc ngưỡng kích hoạt: 50%, 75%, 100% người theo dõi
        double threshold50 = (double) totalFollowers / 2.0;
        double threshold75 = ((double) totalFollowers * 3.0) / 4.0;
        double threshold100 = (double) totalFollowers;

        // 5. Xác định mốc cảnh báo hiện tại
        String currentMilestone = "NONE";
        boolean isAlertTriggered = false;

        if (totalPenaltyScore >= threshold100 && totalPenaltyScore > 0) {
            currentMilestone = "ALERT_100_PERCENT";
            isAlertTriggered = true;
        } else if (totalPenaltyScore >= threshold75 && totalPenaltyScore > 0) {
            currentMilestone = "ALERT_75_PERCENT";
            isAlertTriggered = true;
        } else if (totalPenaltyScore >= threshold50 && totalPenaltyScore > 0) {
            currentMilestone = "ALERT_50_PERCENT";
            isAlertTriggered = true;
        }

        // 6. Xây dựng diễn giải phân tích trực quan
        String summary = buildAnalysisSummary(
                totalFollowers, totalPenaltyScore, oneStarCount, twoStarCount,
                threshold50, threshold75, threshold100, currentMilestone
        );

        return RatingFrustrationAnalysisResponse.builder()
                .ticketId(ticketId)
                .totalRatings(totalRatings)
                .oneStarCount(oneStarCount)
                .twoStarCount(twoStarCount)
                .threeStarCount(threeStarCount)
                .fourStarCount(fourStarCount)
                .fiveStarCount(fiveStarCount)
                .averageRating(averageRating)
                .penaltyPerOneStar(2)
                .penaltyPerTwoStar(1)
                .totalPenaltyScore(totalPenaltyScore)
                .totalFollowers(totalFollowers)
                .threshold50(threshold50)
                .threshold75(threshold75)
                .threshold100(threshold100)
                .isAlertTriggered(isAlertTriggered)
                .currentMilestone(currentMilestone)
                .analysisSummary(summary)
                .build();
    }

    @Override
    @Transactional
    public void checkAndTriggerCrisisAlert(UUID ticketId) {
        RatingFrustrationAnalysisResponse analysis = analyzeFrustration(ticketId);
        if (!analysis.isAlertTriggered() || "NONE".equals(analysis.getCurrentMilestone())) {
            log.info("Ticket {} has penalty score {} < threshold 50% ({}). No alert triggered.",
                    ticketId, analysis.getTotalPenaltyScore(), analysis.getThreshold50());
            return;
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        String milestone = analysis.getCurrentMilestone();
        String milestoneTag = "[" + milestone + "]";

        // Kiểm tra xem mốc này đã được gửi thông báo trước đó chưa để tránh trùng lặp
        List<Notification> existingNotifications = notificationRepository.findByTicket_TicketId(ticketId);
        boolean alreadyNotifiedThisMilestone = existingNotifications.stream()
                .anyMatch(n -> n.getTitle() != null && n.getTitle().contains(milestoneTag));

        if (alreadyNotifiedThisMilestone) {
            log.info("Ticket {} already received notification for milestone {}. Skipping alert.", ticketId, milestone);
            return;
        }

        // Tìm danh sách Manager phụ trách vé
        List<User> managers = resolveTicketManagers(ticket);
        if (managers.isEmpty()) {
            log.warn("No active managers found for ticketId={}. Cannot send alert notification.", ticketId);
            return;
        }

        // Soạn tiêu đề và nội dung cảnh báo theo từng mốc
        String ticketIdShort = ticketId.toString().substring(0, 8);
        String desc = ticket.getDescription() != null ? ticket.getDescription() : "Sự cố";
        if (desc.length() > 60) {
            desc = desc.substring(0, 57) + "...";
        }

        String title;
        String content;

        if ("ALERT_100_PERCENT".equals(milestone)) {
            title = "🔥 BÁO ĐỘNG ĐỎ " + milestoneTag + ": Khủng hoảng mức độ nghiêm trọng (#" + ticketIdShort + ")";
            content = "Báo động đỏ: Mức độ bức xúc của người dân đối với sự cố '" + desc
                    + "' đã chạm mức 100% số người theo dõi (Điểm phạt: "
                    + analysis.getTotalPenaltyScore() + "/" + analysis.getTotalFollowers()
                    + "). Yêu cầu Quản lý vào cuộc ngay lập tức!";
        } else if ("ALERT_75_PERCENT".equals(milestone)) {
            title = "🚨 Cảnh báo khẩn cấp " + milestoneTag + ": Mức độ bức xúc tăng cao (#" + ticketIdShort + ")";
            content = "Cảnh báo khẩn cấp: Sự cố '" + desc + "' có mức độ bức xúc tăng mạnh lên "
                    + analysis.getTotalPenaltyScore() + " điểm phạt (Ngưỡng 75%: " + analysis.getThreshold75()
                    + ", Người theo dõi: " + analysis.getTotalFollowers() + "). Đề nghị Quản lý xử lý khẩn cấp!";
        } else {
            title = "⚠️ Cảnh báo khủng hoảng " + milestoneTag + ": Chỉ số bức xúc đánh giá vượt ngưỡng (#" + ticketIdShort + ")";
            content = "Sự cố '" + desc + "' đã đạt tổng điểm phạt bức xúc là " + analysis.getTotalPenaltyScore()
                    + " (Ngưỡng 50%: " + analysis.getThreshold50() + ", Người theo dõi: " + analysis.getTotalFollowers()
                    + ", Chi tiết: " + analysis.getOneStarCount() + " đánh giá 1★, " + analysis.getTwoStarCount()
                    + " đánh giá 2★). Đề nghị Quản lý kiểm tra và can thiệp!";
        }

        // Gửi thông báo lưu vào Database và đẩy realtime WebSocket (HOÀN TOÀN KHÔNG GỬI MAIL)
        notificationService.createAndSendNotification(ticket, managers, title, content);
        log.info("Sent frustration crisis alert [{}] for ticketId={}, penaltyScore={}, managerCount={}",
                milestone, ticketId, analysis.getTotalPenaltyScore(), managers.size());
    }

    private List<User> resolveTicketManagers(Ticket ticket) {
        Set<UUID> departmentIds = new LinkedHashSet<>();

        // 1. Lấy phòng ban từ cán bộ phụ trách các bước xử lý của vé
        if (ticket.getSteps() != null) {
            for (TicketStep step : ticket.getSteps()) {
                if (step != null && !step.isDeleted() && step.getAssignee() != null) {
                    User assignee = step.getAssignee();
                    if (assignee.getDepartment() != null && assignee.getDepartment().getDepartmentId() != null) {
                        departmentIds.add(assignee.getDepartment().getDepartmentId());
                    }
                    if (assignee.getUserId() != null) {
                        List<Department> userDepts = userDepartmentRepository.findDepartmentsByUserId(assignee.getUserId());
                        if (userDepts != null) {
                            for (Department d : userDepts) {
                                if (d != null && !d.isDeleted() && d.getDepartmentId() != null) {
                                    departmentIds.add(d.getDepartmentId());
                                }
                            }
                        }
                    }
                }
            }
        }

        Map<UUID, User> managersMap = new LinkedHashMap<>();

        if (!departmentIds.isEmpty()) {
            List<User> udManagers = userDepartmentRepository.findActiveManagersByDepartmentIds(departmentIds);
            if (udManagers != null) {
                for (User mgr : udManagers) {
                    if (mgr != null && mgr.getUserId() != null) {
                        managersMap.put(mgr.getUserId(), mgr);
                    }
                }
            }

            List<User> directManagers = userRepository.findActiveManagersByDepartmentIds(departmentIds);
            if (directManagers != null) {
                for (User mgr : directManagers) {
                    if (mgr != null && mgr.getUserId() != null) {
                        managersMap.put(mgr.getUserId(), mgr);
                    }
                }
            }
        }

        // Fallback: Nếu không tìm thấy phòng ban từ steps, tìm tất cả Manager đang ACTIVE trong hệ thống
        if (managersMap.isEmpty()) {
            List<User> allUsers = userRepository.findAllByIsDeletedFalse();
            for (User u : allUsers) {
                if (u.getStatus() == UserStatus.ACTIVE && u.getRole() != null
                        && "MANAGER".equalsIgnoreCase(u.getRole().getRoleName())) {
                    managersMap.put(u.getUserId(), u);
                }
            }
        }

        return new ArrayList<>(managersMap.values());
    }

    private String buildAnalysisSummary(
            int totalFollowers, int totalPenaltyScore, int oneStarCount, int twoStarCount,
            double threshold50, double threshold75, double threshold100, String currentMilestone
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Vé có ").append(totalFollowers).append(" người theo dõi. ");
        sb.append("Tổng điểm phạt bức xúc hiện tại là ").append(totalPenaltyScore);
        sb.append(" (").append(oneStarCount).append(" đánh giá 1 sao [x2đ], ");
        sb.append(twoStarCount).append(" đánh giá 2 sao [x1đ]). ");

        if ("ALERT_100_PERCENT".equals(currentMilestone)) {
            sb.append("BÁO ĐỘNG ĐỎ: Đã chạm mốc 100% người theo dõi (ngưỡng: ")
                    .append(threshold100).append("). Hệ thống kích hoạt cảnh báo khẩn cấp tối đa tới Manager.");
        } else if ("ALERT_75_PERCENT".equals(currentMilestone)) {
            sb.append("CẢNH BÁO TĂNG CẤP: Đã vượt ngưỡng 75% người theo dõi (ngưỡng: ")
                    .append(threshold75).append("). Hệ thống đã gửi cảnh báo nâng cấp tới Manager.");
        } else if ("ALERT_50_PERCENT".equals(currentMilestone)) {
            sb.append("CẢNH BÁO KHỦNG HOẢNG: Đã vượt ngưỡng 50% người theo dõi (ngưỡng: ")
                    .append(threshold50).append("). Hệ thống đã gửi cảnh báo ban đầu tới Manager.");
        } else {
            sb.append("TRẠNG THÁI AN TOÀN: Điểm phạt chưa chạm ngưỡng cảnh báo 50% (ngưỡng: ")
                    .append(threshold50).append(").");
        }

        return sb.toString();
    }
}
