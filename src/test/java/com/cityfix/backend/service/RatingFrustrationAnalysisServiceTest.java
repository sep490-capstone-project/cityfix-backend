package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.response.RatingFrustrationAnalysisResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.service.impl.RatingFrustrationAnalysisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingFrustrationAnalysisServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private TicketVoteRepository ticketVoteRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDepartmentRepository userDepartmentRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RatingFrustrationAnalysisServiceImpl frustrationService;

    private UUID ticketId;
    private Ticket ticket;
    private User creator;
    private List<User> upvoters;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();

        creator = new User();
        creator.setUserId(UUID.randomUUID());
        creator.setFullName("Nguyễn Văn Dân");

        ticket = Ticket.builder()
                .ticketId(ticketId)
                .user(creator)
                .description("Hố ga mất nắp gây nguy hiểm tại đường số 5")
                .build();

        // Giả lập 9 người upvote khác -> Tổng người theo dõi = {Creator} + 9 Upvoters = 10
        upvoters = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            User u = new User();
            u.setUserId(UUID.randomUUID());
            upvoters.add(u);
        }
    }

    @Test
    @DisplayName("Chống False Positive (Bug 1): Vé chỉ có 1 người theo dõi và 1 đánh giá 1 sao -> Không kích hoạt khủng hoảng")
    void testAnalyzeFrustration_SmallSampleSize_OneFollower_NoCrisisAlert() {
        // Arrange: Chỉ có 1 người theo dõi (chính người tạo vé, 0 upvoters)
        List<Rating> ratings = List.of(Rating.builder().rate(1).build());

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.findUpvotersByTicketId(ticketId)).thenReturn(Collections.emptyList());
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(ratings);

        // Act
        RatingFrustrationAnalysisResponse response = frustrationService.analyzeFrustration(ticketId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getTotalFollowers()).isEqualTo(1);
        assertThat(response.getTotalPenaltyScore()).isEqualTo(2); // 1 x 2 = 2
        // Cần tối thiểu 3 người theo dõi và 3 điểm phạt để cấu thành khủng hoảng
        assertThat(response.isAlertTriggered()).isFalse();
        assertThat(response.getCurrentMilestone()).isEqualTo("NONE");
        assertThat(response.getAnalysisSummary()).contains("CHƯA ĐỦ CỠ MẪU");
    }

    @Test
    @DisplayName("Chống False Positive: Vé có 2 người theo dõi và 2 đánh giá 1 sao -> Vẫn chưa đủ quy mô đám đông để gọi là khủng hoảng")
    void testAnalyzeFrustration_SmallSampleSize_TwoFollowers_NoCrisisAlert() {
        User otherUpvoter = new User();
        otherUpvoter.setUserId(UUID.randomUUID());

        List<Rating> ratings = List.of(
                Rating.builder().rate(1).build(),
                Rating.builder().rate(1).build()
        );

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.findUpvotersByTicketId(ticketId)).thenReturn(List.of(otherUpvoter));
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(ratings);

        RatingFrustrationAnalysisResponse response = frustrationService.analyzeFrustration(ticketId);

        assertThat(response.getTotalFollowers()).isEqualTo(2);
        assertThat(response.isAlertTriggered()).isFalse();
        assertThat(response.getCurrentMilestone()).isEqualTo("NONE");
        assertThat(response.getAnalysisSummary()).contains("CHƯA ĐỦ CỠ MẪU");
    }

    @Test
    @DisplayName("Kịch bản 1: 10 người theo dõi, 5 người cho 2 sao -> 5 điểm phạt >= ngưỡng 5.0 -> Kích hoạt cảnh báo 50%")
    void testAnalyzeFrustration_Scenario1_FiveTwoStarRatings() {
        // Arrange: 5 đánh giá 2 sao
        List<Rating> ratings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ratings.add(Rating.builder().rate(2).build());
        }

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.findUpvotersByTicketId(ticketId)).thenReturn(upvoters);
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(ratings);

        // Act
        RatingFrustrationAnalysisResponse response = frustrationService.analyzeFrustration(ticketId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getTotalFollowers()).isEqualTo(10);
        assertThat(response.getTwoStarCount()).isEqualTo(5);
        assertThat(response.getOneStarCount()).isEqualTo(0);
        assertThat(response.getTotalPenaltyScore()).isEqualTo(5); // 5 x 1 = 5
        assertThat(response.getThreshold50()).isEqualTo(5.0);
        assertThat(response.isAlertTriggered()).isTrue();
        assertThat(response.getCurrentMilestone()).isEqualTo("ALERT_50_PERCENT");
        assertThat(response.getAnalysisSummary()).contains("CẢNH BÁO KHỦNG HOẢNG");
    }

    @Test
    @DisplayName("Kịch bản 2: 10 người theo dõi, chỉ cần 3 người cho 1 sao -> 6 điểm phạt >= ngưỡng 5.0 -> Kích hoạt cảnh báo tức thì")
    void testAnalyzeFrustration_Scenario2_ThreeOneStarRatings() {
        // Arrange: 3 đánh giá 1 sao (cực kỳ bức xúc)
        List<Rating> ratings = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ratings.add(Rating.builder().rate(1).build());
        }

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.findUpvotersByTicketId(ticketId)).thenReturn(upvoters);
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(ratings);

        // Act
        RatingFrustrationAnalysisResponse response = frustrationService.analyzeFrustration(ticketId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getTotalFollowers()).isEqualTo(10);
        assertThat(response.getOneStarCount()).isEqualTo(3);
        assertThat(response.getTotalPenaltyScore()).isEqualTo(6); // 3 x 2 = 6
        assertThat(response.getThreshold50()).isEqualTo(5.0);
        assertThat(response.isAlertTriggered()).isTrue();
        assertThat(response.getCurrentMilestone()).isEqualTo("ALERT_50_PERCENT");
        assertThat(response.getAnalysisSummary()).contains("CẢNH BÁO KHỦNG HOẢNG");
    }

    @Test
    @DisplayName("Kịch bản 3: Leo thang mốc 75% -> 4 người cho 1 sao = 8 điểm phạt >= 7.5 -> Kích hoạt ALERT_75_PERCENT")
    void testAnalyzeFrustration_Scenario3_EscalationTo75Percent() {
        // Arrange: 4 đánh giá 1 sao (4 x 2 = 8 điểm phạt)
        List<Rating> ratings = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ratings.add(Rating.builder().rate(1).build());
        }

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.findUpvotersByTicketId(ticketId)).thenReturn(upvoters);
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(ratings);

        // Act
        RatingFrustrationAnalysisResponse response = frustrationService.analyzeFrustration(ticketId);

        // Assert
        assertThat(response.getTotalPenaltyScore()).isEqualTo(8);
        assertThat(response.getThreshold75()).isEqualTo(7.5);
        assertThat(response.isAlertTriggered()).isTrue();
        assertThat(response.getCurrentMilestone()).isEqualTo("ALERT_75_PERCENT");
        assertThat(response.getAnalysisSummary()).contains("CẢNH BÁO TĂNG CẤP");
    }

    @Test
    @DisplayName("Kịch bản 4: Leo thang mốc 100% -> 5 người cho 1 sao = 10 điểm phạt >= 10.0 -> Kích hoạt ALERT_100_PERCENT")
    void testAnalyzeFrustration_Scenario4_EscalationTo100Percent() {
        // Arrange: 5 đánh giá 1 sao (5 x 2 = 10 điểm phạt)
        List<Rating> ratings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ratings.add(Rating.builder().rate(1).build());
        }

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.findUpvotersByTicketId(ticketId)).thenReturn(upvoters);
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(ratings);

        // Act
        RatingFrustrationAnalysisResponse response = frustrationService.analyzeFrustration(ticketId);

        // Assert
        assertThat(response.getTotalPenaltyScore()).isEqualTo(10);
        assertThat(response.getThreshold100()).isEqualTo(10.0);
        assertThat(response.isAlertTriggered()).isTrue();
        assertThat(response.getCurrentMilestone()).isEqualTo("ALERT_100_PERCENT");
        assertThat(response.getAnalysisSummary()).contains("BÁO ĐỘNG ĐỎ");
    }

    @Test
    @DisplayName("Kịch bản 5: An toàn -> 10 người theo dõi, 2 người cho 1 sao = 4 điểm phạt < 5.0 -> Chưa chạm ngưỡng")
    void testAnalyzeFrustration_Scenario5_SafeBelowThreshold() {
        // Arrange: 2 đánh giá 1 sao (2 x 2 = 4 điểm phạt)
        List<Rating> ratings = List.of(
                Rating.builder().rate(1).build(),
                Rating.builder().rate(1).build()
        );

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.findUpvotersByTicketId(ticketId)).thenReturn(upvoters);
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(ratings);

        // Act
        RatingFrustrationAnalysisResponse response = frustrationService.analyzeFrustration(ticketId);

        // Assert
        assertThat(response.getTotalPenaltyScore()).isEqualTo(4);
        assertThat(response.isAlertTriggered()).isFalse();
        assertThat(response.getCurrentMilestone()).isEqualTo("NONE");
        assertThat(response.getAnalysisSummary()).contains("TRẠNG THÁI AN TOÀN");
    }

    @Test
    @DisplayName("Đánh giá 3, 4, 5 sao nhận 0 điểm phạt")
    void testAnalyzeFrustration_ThreeFourFiveStarsZeroPenalty() {
        List<Rating> ratings = List.of(
                Rating.builder().rate(3).build(),
                Rating.builder().rate(4).build(),
                Rating.builder().rate(5).build()
        );

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.findUpvotersByTicketId(ticketId)).thenReturn(upvoters);
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(ratings);

        RatingFrustrationAnalysisResponse response = frustrationService.analyzeFrustration(ticketId);

        assertThat(response.getTotalPenaltyScore()).isEqualTo(0);
        assertThat(response.isAlertTriggered()).isFalse();
    }

    @Test
    @DisplayName("Khử trùng lặp: Người tạo vé tự upvote vé của mình -> Tập người theo dõi tính là 1")
    void testAnalyzeFrustration_CreatorAndUpvoterDeduplication() {
        // Upvoters chỉ chứa chính người tạo vé
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.findUpvotersByTicketId(ticketId)).thenReturn(List.of(creator));
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(Collections.emptyList());

        RatingFrustrationAnalysisResponse response = frustrationService.analyzeFrustration(ticketId);

        assertThat(response.getTotalFollowers()).isEqualTo(1);
    }

    @Test
    @DisplayName("Gửi thông báo lưu DB tới Manager khi vượt ngưỡng mốc 50% (không gửi mail)")
    void testCheckAndTriggerCrisisAlert_SendsNotificationToManagers() {
        // Arrange
        List<Rating> ratings = List.of(
                Rating.builder().rate(1).build(),
                Rating.builder().rate(1).build(),
                Rating.builder().rate(1).build()
        ); // 6 điểm >= 5.0

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.findUpvotersByTicketId(ticketId)).thenReturn(upvoters);
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(ratings);
        when(notificationRepository.findByTicket_TicketId(ticketId)).thenReturn(Collections.emptyList());

        // Giả lập tìm được 1 Manager trong hệ thống
        User manager = new User();
        manager.setUserId(UUID.randomUUID());
        manager.setStatus(UserStatus.ACTIVE);
        Role role = new Role();
        role.setRoleName("MANAGER");
        manager.setRole(role);

        when(userRepository.findAllByIsDeletedFalse()).thenReturn(List.of(manager));

        // Act
        frustrationService.checkAndTriggerCrisisAlert(ticketId);

        // Assert: NotificationService được gọi để lưu DB & gửi WebSocket
        verify(notificationService, times(1)).createAndSendNotification(
                eq(ticket),
                argThat(recipients -> recipients.size() == 1),
                contains("[ALERT_50_PERCENT]"),
                contains("6")
        );
    }

    @Test
    @DisplayName("Không gửi trùng lặp thông báo nếu mốc đó đã được gửi trước đó")
    void testCheckAndTriggerCrisisAlert_SkipsDuplicateMilestone() {
        // Arrange
        List<Rating> ratings = List.of(
                Rating.builder().rate(1).build(),
                Rating.builder().rate(1).build(),
                Rating.builder().rate(1).build()
        ); // 6 điểm >= 5.0 (mốc 50%)

        Notification existingNotif = Notification.builder()
                .title("⚠️ Cảnh báo khủng hoảng [ALERT_50_PERCENT]: Chỉ số bức xúc đánh giá vượt ngưỡng")
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.findUpvotersByTicketId(ticketId)).thenReturn(upvoters);
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(ratings);
        when(notificationRepository.findByTicket_TicketId(ticketId)).thenReturn(List.of(existingNotif));

        // Act
        frustrationService.checkAndTriggerCrisisAlert(ticketId);

        // Assert: Không gửi lại thông báo trùng mốc
        verify(notificationService, never()).createAndSendNotification(any(), any(), anyString(), anyString());
    }
}
