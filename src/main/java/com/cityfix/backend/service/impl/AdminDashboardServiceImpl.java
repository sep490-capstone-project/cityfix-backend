package com.cityfix.backend.service.impl;

import com.cityfix.backend.model.dto.request.AdminTicketFilterRequest;
import com.cityfix.backend.model.dto.response.AdminAnalyticsResponse;
import com.cityfix.backend.model.dto.response.AdminAnalyticsResponse.*;
import com.cityfix.backend.model.dto.response.AdminDashboardSummaryResponse;
import com.cityfix.backend.model.dto.response.AdminDashboardSummaryResponse.*;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.repository.specification.TicketSpecification;
import com.cityfix.backend.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int BATCH_SIZE = 500;
    private static final List<TicketStatus> TERMINAL_NON_ACTIVE_STATUSES = List.of(
            TicketStatus.COMPLETED, TicketStatus.REJECTED, TicketStatus.CANCELLED
    );
    private static final List<TicketStatus> CLOSED_TICKET_STATUSES = List.of(
            TicketStatus.RESOLVED, TicketStatus.COMPLETED, TicketStatus.REJECTED, TicketStatus.CANCELLED
    );

    private final TicketRepository ticketRepository;
    private final TicketStepRepository ticketStepRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final StepRepository stepRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final RatingRepository ratingRepository;

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getDashboardSummary() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Ticket Summary (DB-level aggregation)
        Map<TicketStatus, Long> ticketStatusCounts = new EnumMap<>(TicketStatus.class);
        List<Object[]> rawTicketStatusCounts = ticketRepository.countTicketsGroupByStatus();
        if (rawTicketStatusCounts != null) {
            for (Object[] row : rawTicketStatusCounts) {
                if (row != null && row.length >= 2 && row[0] instanceof TicketStatus status && row[1] instanceof Number cnt) {
                    ticketStatusCounts.put(status, cnt.longValue());
                }
            }
        }

        long initiatedTickets = ticketStatusCounts.getOrDefault(TicketStatus.INITIATED, 0L);
        long assignedTickets = ticketStatusCounts.getOrDefault(TicketStatus.ASSIGNED, 0L);
        long inProgressTickets = ticketStatusCounts.getOrDefault(TicketStatus.IN_PROGRESS, 0L);
        long resolvedTickets = ticketStatusCounts.getOrDefault(TicketStatus.RESOLVED, 0L);
        long completedTickets = ticketStatusCounts.getOrDefault(TicketStatus.COMPLETED, 0L);
        long rejectedTickets = ticketStatusCounts.getOrDefault(TicketStatus.REJECTED, 0L);
        long cancelledTickets = ticketStatusCounts.getOrDefault(TicketStatus.CANCELLED, 0L);
        long totalTickets = ticketStatusCounts.values().stream().mapToLong(Long::longValue).sum();

        long unassignedTickets = ticketRepository.countUnassignedTickets();
        long needsManualReviewTickets = ticketRepository.countByNeedsManualReviewTrue();
        long lifeThreateningTickets = ticketRepository.countActiveLifeThreateningTickets(TERMINAL_NON_ACTIVE_STATUSES);
        long overdueTickets = ticketRepository.countOverdueTickets(now, CLOSED_TICKET_STATUSES);

        long validTickets = totalTickets - rejectedTickets - cancelledTickets;
        double overallResolutionRate = validTickets > 0
                ? round2(((double) (resolvedTickets + completedTickets) / validTickets) * 100.0)
                : 0.0;

        Double avgRatingRaw = ratingRepository.findAverageActiveRating();
        double averageRating = avgRatingRaw != null ? round2(avgRatingRaw) : 0.0;

        TicketKpiSummary ticketSummary = TicketKpiSummary.builder()
                .totalTickets(totalTickets)
                .initiatedTickets(initiatedTickets)
                .assignedTickets(assignedTickets)
                .inProgressTickets(inProgressTickets)
                .resolvedTickets(resolvedTickets)
                .completedTickets(completedTickets)
                .rejectedTickets(rejectedTickets)
                .cancelledTickets(cancelledTickets)
                .unassignedTickets(unassignedTickets)
                .needsManualReviewTickets(needsManualReviewTickets)
                .lifeThreateningTickets(lifeThreateningTickets)
                .overdueTickets(overdueTickets)
                .overallResolutionRate(overallResolutionRate)
                .averageRating(averageRating)
                .build();

        // 2. Account Summary (DB-level aggregation)
        Map<UserStatus, Long> userStatusCounts = new EnumMap<>(UserStatus.class);
        List<Object[]> rawUserStatusCounts = userRepository.countUsersGroupByStatus();
        if (rawUserStatusCounts != null) {
            for (Object[] row : rawUserStatusCounts) {
                if (row != null && row.length >= 2 && row[0] instanceof UserStatus st && row[1] instanceof Number cnt) {
                    userStatusCounts.put(st, cnt.longValue());
                }
            }
        }
        long totalUsers = userStatusCounts.values().stream().mapToLong(Long::longValue).sum();
        long activeUsers = userStatusCounts.getOrDefault(UserStatus.ACTIVE, 0L);
        long blockedUsers = userStatusCounts.getOrDefault(UserStatus.BLOCKED, 0L);

        Map<String, Long> roleCounts = new HashMap<>();
        List<Object[]> rawUserRoleCounts = userRepository.countUsersGroupByRole();
        if (rawUserRoleCounts != null) {
            for (Object[] row : rawUserRoleCounts) {
                if (row != null && row.length >= 2 && row[0] != null && row[1] instanceof Number cnt) {
                    roleCounts.put(row[0].toString().toUpperCase(), cnt.longValue());
                }
            }
        }
        long residentCount = roleCounts.getOrDefault("RESIDENT", 0L);
        long staffCount = roleCounts.getOrDefault("STAFF", 0L);
        long managerCount = roleCounts.getOrDefault("MANAGER", 0L);
        long adminCount = roleCounts.getOrDefault("ADMIN", 0L);

        AccountKpiSummary accountSummary = AccountKpiSummary.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .blockedUsers(blockedUsers)
                .residentCount(residentCount)
                .staffCount(staffCount)
                .managerCount(managerCount)
                .adminCount(adminCount)
                .build();

        // 3. Config Summary (DB-level counts & active rules)
        List<Department> activeDepartments = departmentRepository.findAllByIsDeletedFalse();
        long totalCategories = categoryRepository.countByIsDeletedFalse();
        long activeCategoriesCount = categoryRepository.countByIsActiveTrueAndIsDeletedFalse();
        long totalSubCategories = subCategoryRepository.countByIsDeletedFalse();
        long activeSubCategoriesCount = subCategoryRepository.countByIsActiveTrueAndIsDeletedFalse();
        long totalSteps = stepRepository.countByIsDeletedFalse();
        List<RoutingRule> activeRoutingRules = routingRuleRepository.findByIsDeletedFalse();
        long totalRoutingRules = activeRoutingRules.size();

        ConfigKpiSummary configSummary = ConfigKpiSummary.builder()
                .totalDepartments(activeDepartments.size())
                .totalCategories(totalCategories)
                .activeCategories(activeCategoriesCount)
                .totalSubCategories(totalSubCategories)
                .activeSubCategories(activeSubCategoriesCount)
                .totalSteps(totalSteps)
                .totalRoutingRules(totalRoutingRules)
                .build();

        // 4. Department Summaries (batched ticket & step processing without holding all tickets in memory)
        List<DepartmentKpiItem> departmentSummaries = buildDepartmentKpiItemsInBatches(
                activeDepartments, activeRoutingRules, now
        );

        return AdminDashboardSummaryResponse.builder()
                .ticketSummary(ticketSummary)
                .accountSummary(accountSummary)
                .configSummary(configSummary)
                .departmentSummaries(departmentSummaries)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAnalytics(
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String groupBy,
            String province,
            String ward,
            UUID departmentId,
            UUID categoryId) {

        LocalDateTime now = LocalDateTime.now();

        AdminTicketFilterRequest filter = AdminTicketFilterRequest.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .province(province)
                .ward(ward)
                .departmentId(departmentId)
                .categoryId(categoryId)
                .build();

        List<Ticket> tickets = new ArrayList<>();
        Map<UUID, List<TicketStep>> stepsByTicket = new HashMap<>();
        Map<UUID, List<Rating>> ratingsByTicket = new HashMap<>();

        processTicketBatches(TicketSpecification.withFilter(filter), (batchTickets, batchStepsMap) -> {
            tickets.addAll(batchTickets);
            stepsByTicket.putAll(batchStepsMap);

            List<UUID> batchIds = batchTickets.stream()
                    .map(Ticket::getTicketId)
                    .filter(Objects::nonNull)
                    .toList();
            if (!batchIds.isEmpty()) {
                List<Rating> batchRatings = ratingRepository.findByTicket_TicketIdInAndIsDeletedFalse(batchIds);
                if (batchRatings != null) {
                    for (Rating r : batchRatings) {
                        if (r != null && !r.isDeleted() && r.getTicket() != null && r.getTicket().getTicketId() != null) {
                            ratingsByTicket.computeIfAbsent(r.getTicket().getTicketId(), k -> new ArrayList<>()).add(r);
                        }
                    }
                }
            }
        });

        List<RoutingRule> activeRoutingRules = routingRuleRepository.findByIsDeletedFalse();

        // 1. Overview
        AnalyticsOverview overview = buildAnalyticsOverview(tickets, stepsByTicket, ratingsByTicket, now);

        // 2. Time Series
        List<TimeSeriesItem> timeSeries = buildTimeSeries(tickets, groupBy);

        // 3. Geographic Breakdown
        List<GeographicAnalyticsItem> geographicBreakdown = buildGeographicBreakdown(tickets, now);

        // 4. Department Breakdown
        List<Department> departments = departmentRepository.findAllByIsDeletedFalse().stream()
                .filter(d -> departmentId == null || departmentId.equals(d.getDepartmentId()))
                .toList();
        List<DepartmentAnalyticsItem> departmentBreakdown = buildDepartmentBreakdown(
                departments, tickets, stepsByTicket, ratingsByTicket, activeRoutingRules, now
        );

        // 5. Category Breakdown
        List<Category> categories = categoryRepository.findAllByIsDeletedFalse().stream()
                .filter(c -> categoryId == null || categoryId.equals(c.getCategoryId()))
                .toList();
        List<CategoryAnalyticsItem> categoryBreakdown = buildCategoryBreakdown(categories, tickets);

        // 6. Status & Priority Breakdown
        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        for (TicketStatus st : TicketStatus.values()) {
            statusBreakdown.put(st.name(), countByStatus(tickets, st));
        }

        Map<String, Long> priorityBreakdown = new LinkedHashMap<>();
        for (Priority p : Priority.values()) {
            long cnt = tickets.stream().filter(t -> t.getPriority() == p).count();
            priorityBreakdown.put(p.name(), cnt);
        }

        return AdminAnalyticsResponse.builder()
                .overview(overview)
                .timeSeries(timeSeries)
                .geographicBreakdown(geographicBreakdown)
                .departmentBreakdown(departmentBreakdown)
                .categoryBreakdown(categoryBreakdown)
                .statusBreakdown(statusBreakdown)
                .priorityBreakdown(priorityBreakdown)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportAnalyticsCsv(
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String groupBy,
            String province,
            String ward,
            UUID departmentId,
            UUID categoryId) {

        AdminAnalyticsResponse analytics = getAnalytics(fromDate, toDate, groupBy, province, ward, departmentId, categoryId);

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM for Microsoft Excel compatibility with Vietnamese characters
        sb.append('\uFEFF');

        // Section 1: Overview
        sb.append("=== BAO CAO TONG QUAN HE THONG (OVERVIEW) ===\n");
        sb.append("Chi so,Gia tri\n");
        AnalyticsOverview ov = analytics.getOverview();
        if (ov != null) {
            sb.append("Tong so ho so,").append(ov.getTotalTickets()).append("\n");
            sb.append("Ho so da giai quyet (RESOLVED/COMPLETED),").append(ov.getResolvedOrCompletedTickets()).append("\n");
            sb.append("Ho so dang xu ly,").append(ov.getInProgressTickets()).append("\n");
            sb.append("Ho so qua han (Overdue),").append(ov.getOverdueTickets()).append("\n");
            sb.append("Ho so tu choi/huy,").append(ov.getRejectedOrCancelledTickets()).append("\n");
            sb.append("Ty le giai quyet (%),").append(ov.getResolutionRate()).append("\n");
            sb.append("Ty le dung han SLA (%),").append(ov.getOnTimeResolutionRate()).append("\n");
            sb.append("Thoi gian xu ly trung binh (Gio),").append(ov.getAverageResolutionHours()).append("\n");
            sb.append("Diem danh gia trung binh,").append(ov.getAverageRating()).append("\n");
        }
        sb.append("\n");

        // Section 2: Time Series
        sb.append("=== XU HUONG SU CO THEO THOI GIAN (TIME SERIES) ===\n");
        sb.append("Ky (Period),Tong tiep nhan,Da giai quyet,Dang xu ly,Tu choi/Huy\n");
        if (analytics.getTimeSeries() != null) {
            for (TimeSeriesItem ts : analytics.getTimeSeries()) {
                sb.append(escapeCsv(ts.getPeriod())).append(",")
                        .append(ts.getTotalCreated()).append(",")
                        .append(ts.getResolvedOrCompleted()).append(",")
                        .append(ts.getInProgress()).append(",")
                        .append(ts.getRejectedOrCancelled()).append("\n");
            }
        }
        sb.append("\n");

        // Section 3: Geographic Breakdown
        sb.append("=== THONG KE THEO KHU VUC DIA LY (GEOGRAPHIC BREAKDOWN) ===\n");
        sb.append("Tinh/Thanh pho,Phuong/Xa,Tong ho so,Da giai quyet,Dang xu ly,Qua han,Ty le giai quyet (%)\n");
        if (analytics.getGeographicBreakdown() != null) {
            for (GeographicAnalyticsItem geo : analytics.getGeographicBreakdown()) {
                sb.append(escapeCsv(geo.getProvince())).append(",")
                        .append(escapeCsv(geo.getWard())).append(",")
                        .append(geo.getTotalTickets()).append(",")
                        .append(geo.getResolvedOrCompletedTickets()).append(",")
                        .append(geo.getInProgressTickets()).append(",")
                        .append(geo.getOverdueTickets()).append(",")
                        .append(geo.getResolutionRate()).append("\n");
            }
        }
        sb.append("\n");

        // Section 4: Department Breakdown
        sb.append("=== HIEU QUA HOAT DONG CO QUAN PHU TRACH (DEPARTMENT PERFORMANCE) ===\n");
        sb.append("Ma co quan,Ten co quan,Tong ho so,Da giai quyet,Dang xu ly,Qua han,Ty le giai quyet (%),Ty le dung han SLA (%),Thoi gian xu ly TB (Gio),Diem danh gia TB\n");
        if (analytics.getDepartmentBreakdown() != null) {
            for (DepartmentAnalyticsItem dept : analytics.getDepartmentBreakdown()) {
                sb.append(escapeCsv(dept.getDepartmentId() != null ? dept.getDepartmentId().toString() : "")).append(",")
                        .append(escapeCsv(dept.getDepartmentName())).append(",")
                        .append(dept.getTotalTickets()).append(",")
                        .append(dept.getResolvedOrCompletedTickets()).append(",")
                        .append(dept.getInProgressTickets()).append(",")
                        .append(dept.getOverdueTickets()).append(",")
                        .append(dept.getResolutionRate()).append(",")
                        .append(dept.getOnTimeRate()).append(",")
                        .append(dept.getAverageResolutionHours()).append(",")
                        .append(dept.getAverageRating()).append("\n");
            }
        }
        sb.append("\n");

        // Section 5: Category Breakdown
        sb.append("=== THONG KE THEO DANH MUC SU CO (CATEGORY BREAKDOWN) ===\n");
        sb.append("Ma danh muc,Ten danh muc,Tong ho so,Da giai quyet,Ty le giai quyet (%)\n");
        if (analytics.getCategoryBreakdown() != null) {
            for (CategoryAnalyticsItem cat : analytics.getCategoryBreakdown()) {
                sb.append(escapeCsv(cat.getCategoryId() != null ? cat.getCategoryId().toString() : "")).append(",")
                        .append(escapeCsv(cat.getCategoryName())).append(",")
                        .append(cat.getTotalTickets()).append(",")
                        .append(cat.getResolvedOrCompletedTickets()).append(",")
                        .append(cat.getResolutionRate()).append("\n");
            }
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void processTicketBatches(
            Specification<Ticket> spec,
            BiConsumer<List<Ticket>, Map<UUID, List<TicketStep>>> batchConsumer) {

        int pageNumber = 0;
        Sort sort = Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "ticketId"));
        while (true) {
            Pageable pageable = PageRequest.of(pageNumber, BATCH_SIZE, sort);
            Page<Ticket> page = ticketRepository.findAll(spec, pageable);
            if (page == null || page.isEmpty()) {
                break;
            }
            List<Ticket> batchTickets = page.getContent();
            List<UUID> batchIds = batchTickets.stream()
                    .map(Ticket::getTicketId)
                    .filter(Objects::nonNull)
                    .toList();

            Map<UUID, List<TicketStep>> batchStepsMap = new HashMap<>();
            if (!batchIds.isEmpty()) {
                List<TicketStep> steps = ticketStepRepository.findByTicketTicketIdIn(batchIds);
                if (steps != null) {
                    for (TicketStep step : steps) {
                        if (step != null && step.getTicket() != null && step.getTicket().getTicketId() != null) {
                            batchStepsMap.computeIfAbsent(step.getTicket().getTicketId(), k -> new ArrayList<>()).add(step);
                        }
                    }
                }
            }

            batchConsumer.accept(batchTickets, batchStepsMap);

            if (!page.hasNext()) {
                break;
            }
            pageNumber++;
        }
    }

    private List<DepartmentKpiItem> buildDepartmentKpiItemsInBatches(
            List<Department> departments,
            List<RoutingRule> activeRoutingRules,
            LocalDateTime now) {

        if (departments == null || departments.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, long[]> deptCounters = new LinkedHashMap<>();
        for (Department dept : departments) {
            if (dept != null && dept.getDepartmentId() != null) {
                // [0]=total, [1]=inProgress, [2]=resolvedOrCompleted, [3]=overdue, [4]=valid
                deptCounters.put(dept.getDepartmentId(), new long[5]);
            }
        }

        processTicketBatches(TicketSpecification.withFilter(null), (batchTickets, batchStepsMap) -> {
            for (Ticket ticket : batchTickets) {
                if (ticket == null) {
                    continue;
                }
                TicketStatus st = ticket.getStatus();
                boolean isInProgress = (st == TicketStatus.ASSIGNED || st == TicketStatus.IN_PROGRESS);
                boolean isResolved = isResolvedOrCompleted(st);
                boolean isOverdue = isTicketOverdue(ticket, now);
                boolean isValid = (st != TicketStatus.REJECTED && st != TicketStatus.CANCELLED);

                for (Department dept : departments) {
                    if (dept == null || dept.getDepartmentId() == null) {
                        continue;
                    }
                    UUID deptId = dept.getDepartmentId();
                    if (isTicketBelongingToDepartment(ticket, deptId, batchStepsMap, activeRoutingRules)) {
                        long[] counters = deptCounters.get(deptId);
                        counters[0]++;
                        if (isInProgress) counters[1]++;
                        if (isResolved) counters[2]++;
                        if (isOverdue) counters[3]++;
                        if (isValid) counters[4]++;
                    }
                }
            }
        });

        List<DepartmentKpiItem> items = new ArrayList<>();
        for (Department dept : departments) {
            if (dept == null || dept.getDepartmentId() == null) {
                continue;
            }
            UUID deptId = dept.getDepartmentId();
            long[] counters = deptCounters.getOrDefault(deptId, new long[5]);
            long total = counters[0];
            long inProgress = counters[1];
            long resolvedOrCompleted = counters[2];
            long overdue = counters[3];
            long valid = counters[4];

            double resolutionRate = valid > 0
                    ? round2(((double) resolvedOrCompleted / valid) * 100.0)
                    : 0.0;

            items.add(DepartmentKpiItem.builder()
                    .departmentId(deptId)
                    .departmentName(dept.getName())
                    .totalAssignedTickets(total)
                    .inProgressTickets(inProgress)
                    .resolvedOrCompletedTickets(resolvedOrCompleted)
                    .overdueTickets(overdue)
                    .resolutionRate(resolutionRate)
                    .build());
        }
        return items;
    }

    private AnalyticsOverview buildAnalyticsOverview(
            List<Ticket> tickets,
            Map<UUID, List<TicketStep>> stepsByTicket,
            Map<UUID, List<Rating>> ratingsByTicket,
            LocalDateTime now) {

        long total = tickets.size();
        long resolvedOrCompleted = tickets.stream()
                .filter(t -> isResolvedOrCompleted(t.getStatus()))
                .count();
        long inProgress = tickets.stream()
                .filter(t -> isOpenActiveStatus(t.getStatus()))
                .count();
        long overdue = tickets.stream()
                .filter(t -> isTicketOverdue(t, now))
                .count();
        long rejectedOrCancelled = tickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.REJECTED || t.getStatus() == TicketStatus.CANCELLED)
                .count();

        long validTotal = total - rejectedOrCancelled;
        double resolutionRate = validTotal > 0
                ? round2(((double) resolvedOrCompleted / validTotal) * 100.0)
                : 0.0;

        List<Ticket> resolvedTicketsList = tickets.stream()
                .filter(t -> isResolvedOrCompleted(t.getStatus()))
                .toList();

        List<Ticket> slaEligibleResolvedList = resolvedTicketsList.stream()
                .filter(t -> hasSlaData(t, stepsByTicket))
                .toList();

        long onTimeCount = slaEligibleResolvedList.stream()
                .filter(t -> isResolvedOnTime(t, stepsByTicket))
                .count();

        double onTimeResolutionRate = !slaEligibleResolvedList.isEmpty()
                ? round2(((double) onTimeCount / slaEligibleResolvedList.size()) * 100.0)
                : 0.0;

        double avgResolutionHours = calculateAverageResolutionHours(resolvedTicketsList, stepsByTicket);

        List<Rating> flatRatings = ratingsByTicket.values().stream()
                .flatMap(Collection::stream)
                .filter(r -> r.getRate() != null)
                .toList();

        double avgRating = flatRatings.isEmpty()
                ? 0.0
                : round2(flatRatings.stream().mapToInt(Rating::getRate).average().orElse(0.0));

        return AnalyticsOverview.builder()
                .totalTickets(total)
                .resolvedOrCompletedTickets(resolvedOrCompleted)
                .inProgressTickets(inProgress)
                .overdueTickets(overdue)
                .rejectedOrCancelledTickets(rejectedOrCancelled)
                .resolutionRate(resolutionRate)
                .onTimeResolutionRate(onTimeResolutionRate)
                .averageResolutionHours(avgResolutionHours)
                .averageRating(avgRating)
                .build();
    }

    private List<TimeSeriesItem> buildTimeSeries(List<Ticket> tickets, String groupBy) {
        String mode = (groupBy != null) ? groupBy.trim().toUpperCase() : "DAY";
        Map<String, List<Ticket>> grouped = new TreeMap<>();

        for (Ticket t : tickets) {
            if (t.getCreatedAt() == null) {
                continue;
            }
            String key = formatPeriod(t.getCreatedAt(), mode);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        List<TimeSeriesItem> result = new ArrayList<>();
        for (Map.Entry<String, List<Ticket>> entry : grouped.entrySet()) {
            List<Ticket> periodTickets = entry.getValue();
            long created = periodTickets.size();
            long resolved = periodTickets.stream().filter(t -> isResolvedOrCompleted(t.getStatus())).count();
            long inProg = periodTickets.stream().filter(t -> isOpenActiveStatus(t.getStatus())).count();
            long rejOrCanc = periodTickets.stream()
                    .filter(t -> t.getStatus() == TicketStatus.REJECTED || t.getStatus() == TicketStatus.CANCELLED)
                    .count();

            result.add(TimeSeriesItem.builder()
                    .period(entry.getKey())
                    .totalCreated(created)
                    .resolvedOrCompleted(resolved)
                    .inProgress(inProg)
                    .rejectedOrCancelled(rejOrCanc)
                    .build());
        }
        return result;
    }

    private String formatPeriod(LocalDateTime dateTime, String mode) {
        if ("MONTH".equals(mode)) {
            return dateTime.format(MONTH_FORMATTER);
        } else if ("WEEK".equals(mode)) {
            WeekFields wf = WeekFields.ISO;
            int weekYear = dateTime.get(wf.weekBasedYear());
            int week = dateTime.get(wf.weekOfWeekBasedYear());
            return String.format("%04d-W%02d", weekYear, week);
        }
        return dateTime.format(DAY_FORMATTER);
    }

    private List<GeographicAnalyticsItem> buildGeographicBreakdown(List<Ticket> tickets, LocalDateTime now) {
        Map<String, List<Ticket>> byGeo = new LinkedHashMap<>();
        Map<String, String[]> geoLabels = new HashMap<>();

        for (Ticket t : tickets) {
            String prov = extractLocationPart(t.getAddress(), "PROVINCE");
            String wrd = extractLocationPart(t.getAddress(), "WARD");
            if (prov == null || prov.isBlank()) {
                prov = "Chưa xác định";
            }
            if (wrd == null || wrd.isBlank()) {
                wrd = "Chưa xác định";
            }
            String key = prov.toLowerCase() + "||" + wrd.toLowerCase();
            geoLabels.putIfAbsent(key, new String[]{prov, wrd});
            byGeo.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        List<GeographicAnalyticsItem> items = new ArrayList<>();
        for (Map.Entry<String, List<Ticket>> entry : byGeo.entrySet()) {
            String[] labels = geoLabels.get(entry.getKey());
            List<Ticket> geoTickets = entry.getValue();
            long total = geoTickets.size();
            long resolved = geoTickets.stream().filter(t -> isResolvedOrCompleted(t.getStatus())).count();
            long inProg = geoTickets.stream().filter(t -> isOpenActiveStatus(t.getStatus())).count();
            long overdue = geoTickets.stream().filter(t -> isTicketOverdue(t, now)).count();
            long valid = geoTickets.stream()
                    .filter(t -> t.getStatus() != TicketStatus.REJECTED && t.getStatus() != TicketStatus.CANCELLED)
                    .count();
            double rate = valid > 0 ? round2(((double) resolved / valid) * 100.0) : 0.0;

            items.add(GeographicAnalyticsItem.builder()
                    .province(labels[0])
                    .ward(labels[1])
                    .totalTickets(total)
                    .resolvedOrCompletedTickets(resolved)
                    .inProgressTickets(inProg)
                    .overdueTickets(overdue)
                    .resolutionRate(rate)
                    .build());
        }

        items.sort(Comparator.comparingLong(GeographicAnalyticsItem::getTotalTickets).reversed());
        return items;
    }

    private List<DepartmentAnalyticsItem> buildDepartmentBreakdown(
            List<Department> departments,
            List<Ticket> tickets,
            Map<UUID, List<TicketStep>> stepsByTicket,
            Map<UUID, List<Rating>> ratingsByTicket,
            List<RoutingRule> activeRoutingRules,
            LocalDateTime now) {

        List<DepartmentAnalyticsItem> items = new ArrayList<>();
        for (Department dept : departments) {
            UUID deptId = dept.getDepartmentId();
            List<Ticket> deptTickets = tickets.stream()
                    .filter(t -> isTicketBelongingToDepartment(t, deptId, stepsByTicket, activeRoutingRules))
                    .toList();

            long total = deptTickets.size();
            List<Ticket> resolvedList = deptTickets.stream()
                    .filter(t -> isResolvedOrCompleted(t.getStatus()))
                    .toList();
            long resolved = resolvedList.size();
            long inProgress = deptTickets.stream()
                    .filter(t -> isOpenActiveStatus(t.getStatus()))
                    .count();
            long overdue = deptTickets.stream()
                    .filter(t -> isTicketOverdue(t, now))
                    .count();
            long valid = deptTickets.stream()
                    .filter(t -> t.getStatus() != TicketStatus.REJECTED && t.getStatus() != TicketStatus.CANCELLED)
                    .count();

            double resolutionRate = valid > 0 ? round2(((double) resolved / valid) * 100.0) : 0.0;

            List<Ticket> slaEligibleResolved = resolvedList.stream()
                    .filter(t -> hasSlaData(t, stepsByTicket))
                    .toList();
            long onTime = slaEligibleResolved.stream().filter(t -> isResolvedOnTime(t, stepsByTicket)).count();
            double onTimeRate = !slaEligibleResolved.isEmpty()
                    ? round2(((double) onTime / slaEligibleResolved.size()) * 100.0)
                    : 0.0;

            double avgHours = calculateAverageResolutionHours(resolvedList, stepsByTicket);

            List<Rating> deptRatings = deptTickets.stream()
                    .map(t -> ratingsByTicket.getOrDefault(t.getTicketId(), Collections.emptyList()))
                    .flatMap(Collection::stream)
                    .filter(r -> r.getRate() != null)
                    .toList();
            double avgRating = deptRatings.isEmpty()
                    ? 0.0
                    : round2(deptRatings.stream().mapToInt(Rating::getRate).average().orElse(0.0));

            items.add(DepartmentAnalyticsItem.builder()
                    .departmentId(deptId)
                    .departmentName(dept.getName())
                    .totalTickets(total)
                    .resolvedOrCompletedTickets(resolved)
                    .inProgressTickets(inProgress)
                    .overdueTickets(overdue)
                    .resolutionRate(resolutionRate)
                    .onTimeRate(onTimeRate)
                    .averageResolutionHours(avgHours)
                    .averageRating(avgRating)
                    .build());
        }
        return items;
    }

    private List<CategoryAnalyticsItem> buildCategoryBreakdown(List<Category> categories, List<Ticket> tickets) {
        List<CategoryAnalyticsItem> items = new ArrayList<>();
        for (Category cat : categories) {
            UUID catId = cat.getCategoryId();
            List<Ticket> catTickets = tickets.stream()
                    .filter(t -> isTicketInCategory(t, catId))
                    .toList();

            long total = catTickets.size();
            long resolved = catTickets.stream().filter(t -> isResolvedOrCompleted(t.getStatus())).count();
            long valid = catTickets.stream()
                    .filter(t -> t.getStatus() != TicketStatus.REJECTED && t.getStatus() != TicketStatus.CANCELLED)
                    .count();
            double rate = valid > 0 ? round2(((double) resolved / valid) * 100.0) : 0.0;

            items.add(CategoryAnalyticsItem.builder()
                    .categoryId(catId)
                    .categoryName(cat.getName())
                    .totalTickets(total)
                    .resolvedOrCompletedTickets(resolved)
                    .resolutionRate(rate)
                    .build());
        }
        return items;
    }

    private List<TicketStep> getPreloadedOrInitializedSteps(Ticket ticket, Map<UUID, List<TicketStep>> stepsByTicket) {
        if (ticket == null) {
            return Collections.emptyList();
        }
        if (stepsByTicket != null && ticket.getTicketId() != null) {
            List<TicketStep> preloaded = stepsByTicket.get(ticket.getTicketId());
            if (preloaded != null && !preloaded.isEmpty()) {
                return preloaded;
            }
        }
        if (ticket.getSteps() != null && Hibernate.isInitialized(ticket.getSteps())) {
            return ticket.getSteps();
        }
        return Collections.emptyList();
    }

    private boolean isTicketUnassigned(Ticket ticket, Map<UUID, List<TicketStep>> stepsByTicket) {
        if (ticket == null) {
            return false;
        }
        List<TicketStep> steps = getPreloadedOrInitializedSteps(ticket, stepsByTicket);
        if (steps.isEmpty()) {
            return true;
        }
        return steps.stream()
                .noneMatch(s -> s != null && !s.isDeleted() && s.getAssignee() != null);
    }

    private boolean isTicketBelongingToDepartment(
            Ticket ticket,
            UUID departmentId,
            Map<UUID, List<TicketStep>> stepsByTicket,
            List<RoutingRule> activeRoutingRules) {
        if (ticket == null || departmentId == null) {
            return false;
        }
        if (isTicketAssignedToDepartment(ticket, departmentId, stepsByTicket)) {
            return true;
        }
        if (!isTicketUnassigned(ticket, stepsByTicket) || activeRoutingRules == null || activeRoutingRules.isEmpty()) {
            return false;
        }
        return isUnassignedTicketRoutedToDepartment(ticket, departmentId, activeRoutingRules);
    }

    private boolean isTicketAssignedToDepartment(Ticket ticket, UUID departmentId, Map<UUID, List<TicketStep>> stepsByTicket) {
        if (ticket == null || departmentId == null) {
            return false;
        }
        List<TicketStep> steps = getPreloadedOrInitializedSteps(ticket, stepsByTicket);
        for (TicketStep step : steps) {
            if (step != null && !step.isDeleted() && step.getAssignee() != null) {
                User assignee = step.getAssignee();
                if (assignee.getDepartment() != null && departmentId.equals(assignee.getDepartment().getDepartmentId())) {
                    return true;
                }
                if (assignee.getUserDepartments() != null && Hibernate.isInitialized(assignee.getUserDepartments())) {
                    for (UserDepartment ud : assignee.getUserDepartments()) {
                        if (ud != null && ud.getDepartment() != null && !ud.getDepartment().isDeleted()
                                && departmentId.equals(ud.getDepartment().getDepartmentId())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isUnassignedTicketRoutedToDepartment(
            Ticket ticket,
            UUID departmentId,
            List<RoutingRule> activeRoutingRules) {

        if (ticket.getSubCategories() == null || ticket.getSubCategories().isEmpty()) {
            return false;
        }
        String address = ticket.getAddress() != null ? ticket.getAddress().toLowerCase() : "";
        for (TicketSubCategory tsc : ticket.getSubCategories()) {
            if (tsc == null || tsc.getSubCategory() == null || tsc.getSubCategory().getCategory() == null) {
                continue;
            }
            UUID catId = tsc.getSubCategory().getCategory().getCategoryId();
            if (catId == null) {
                continue;
            }
            for (RoutingRule rule : activeRoutingRules) {
                if (rule == null || rule.isDeleted() || rule.getDepartment() == null || rule.getCategory() == null) {
                    continue;
                }
                if (!departmentId.equals(rule.getDepartment().getDepartmentId())
                        || !catId.equals(rule.getCategory().getCategoryId())) {
                    continue;
                }
                boolean provinceMatch = rule.getProvince() == null || rule.getProvince().isBlank()
                        || address.contains(rule.getProvince().toLowerCase());
                boolean wardMatch = rule.getWard() == null || rule.getWard().isBlank()
                        || address.contains(rule.getWard().toLowerCase());
                if (provinceMatch && wardMatch) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTicketInCategory(Ticket ticket, UUID categoryId) {
        if (ticket == null || categoryId == null || ticket.getSubCategories() == null) {
            return false;
        }
        for (TicketSubCategory tsc : ticket.getSubCategories()) {
            if (tsc != null && tsc.getSubCategory() != null && tsc.getSubCategory().getCategory() != null
                    && categoryId.equals(tsc.getSubCategory().getCategory().getCategoryId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isTicketOverdue(Ticket ticket, LocalDateTime now) {
        if (ticket == null || ticket.getDeadline() == null) {
            return false;
        }
        TicketStatus st = ticket.getStatus();
        if (st == TicketStatus.RESOLVED || st == TicketStatus.COMPLETED
                || st == TicketStatus.REJECTED || st == TicketStatus.CANCELLED) {
            return false;
        }
        return ticket.getDeadline().isBefore(now);
    }

    private boolean hasSlaData(Ticket ticket, Map<UUID, List<TicketStep>> stepsByTicket) {
        return ticket != null && ticket.getDeadline() != null && resolveCompletionTime(ticket, stepsByTicket) != null;
    }

    private boolean isResolvedOnTime(Ticket ticket, Map<UUID, List<TicketStep>> stepsByTicket) {
        if (ticket == null || ticket.getDeadline() == null) {
            return false;
        }
        LocalDateTime resolvedTime = resolveCompletionTime(ticket, stepsByTicket);
        if (resolvedTime == null) {
            return false;
        }
        return !resolvedTime.isAfter(ticket.getDeadline());
    }

    private LocalDateTime resolveCompletionTime(Ticket ticket, Map<UUID, List<TicketStep>> stepsByTicket) {
        List<TicketStep> steps = getPreloadedOrInitializedSteps(ticket, stepsByTicket);
        if (steps.isEmpty()) {
            return null;
        }
        return steps.stream()
                .filter(s -> s != null && !s.isDeleted() && s.getCompletedAt() != null)
                .map(TicketStep::getCompletedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private double calculateAverageResolutionHours(List<Ticket> resolvedTickets, Map<UUID, List<TicketStep>> stepsByTicket) {
        if (resolvedTickets == null || resolvedTickets.isEmpty()) {
            return 0.0;
        }
        List<Double> hoursList = new ArrayList<>();
        for (Ticket t : resolvedTickets) {
            LocalDateTime start = t.getCreatedAt();
            LocalDateTime end = resolveCompletionTime(t, stepsByTicket);
            if (start != null && end != null && !end.isBefore(start)) {
                double hours = Duration.between(start, end).toMinutes() / 60.0;
                hoursList.add(hours);
            }
        }
        if (hoursList.isEmpty()) {
            return 0.0;
        }
        return round2(hoursList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
    }

    private boolean isResolvedOrCompleted(TicketStatus status) {
        return status == TicketStatus.RESOLVED || status == TicketStatus.COMPLETED;
    }

    private boolean isOpenActiveStatus(TicketStatus status) {
        return status == TicketStatus.INITIATED || status == TicketStatus.ASSIGNED || status == TicketStatus.IN_PROGRESS;
    }

    private long countByStatus(List<Ticket> tickets, TicketStatus status) {
        return tickets.stream().filter(t -> t.getStatus() == status).count();
    }

    private String extractLocationPart(String address, String type) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] parts = address.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        int length = parts.length;
        if (length == 0) {
            return null;
        }
        if ("PROVINCE".equalsIgnoreCase(type)) {
            String province = parts[length - 1];
            return province.isEmpty() ? null : province;
        } else if ("WARD".equalsIgnoreCase(type)) {
            if (length >= 3) {
                String ward = parts[length - 3];
                return ward.isEmpty() ? null : ward;
            } else if (length == 2) {
                String ward = parts[0];
                return ward.isEmpty() ? null : ward;
            }
        }
        return null;
    }

    private String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String sanitized = value;
        String trimmedLeading = sanitized.stripLeading();
        if (!trimmedLeading.isEmpty()) {
            char firstChar = trimmedLeading.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@'
                    || sanitized.charAt(0) == '\t' || sanitized.charAt(0) == '\r') {
                sanitized = "'" + sanitized;
            }
        }
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
