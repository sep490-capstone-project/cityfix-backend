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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final TicketRepository ticketRepository;
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

        List<Ticket> allTickets = ticketRepository.findAll(TicketSpecification.withFilter(null));
        List<Rating> allRatings = ratingRepository.findAll().stream()
                .filter(r -> r != null && !r.isDeleted())
                .toList();

        // 1. Ticket Summary
        long totalTickets = allTickets.size();
        long initiatedTickets = countByStatus(allTickets, TicketStatus.INITIATED);
        long assignedTickets = countByStatus(allTickets, TicketStatus.ASSIGNED);
        long inProgressTickets = countByStatus(allTickets, TicketStatus.IN_PROGRESS);
        long resolvedTickets = countByStatus(allTickets, TicketStatus.RESOLVED);
        long completedTickets = countByStatus(allTickets, TicketStatus.COMPLETED);
        long rejectedTickets = countByStatus(allTickets, TicketStatus.REJECTED);
        long cancelledTickets = countByStatus(allTickets, TicketStatus.CANCELLED);

        long unassignedTickets = allTickets.stream()
                .filter(this::isTicketUnassigned)
                .count();

        long needsManualReviewTickets = allTickets.stream()
                .filter(t -> Boolean.TRUE.equals(t.getNeedsManualReview()))
                .count();

        long lifeThreateningTickets = allTickets.stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsLifeThreatening()) && !isTerminalNonActiveStatus(t.getStatus()))
                .count();

        long overdueTickets = allTickets.stream()
                .filter(t -> isTicketOverdue(t, now))
                .count();

        long validTickets = totalTickets - rejectedTickets - cancelledTickets;
        double overallResolutionRate = validTickets > 0
                ? round2(((double) (resolvedTickets + completedTickets) / validTickets) * 100.0)
                : 0.0;

        double averageRating = allRatings.isEmpty()
                ? 0.0
                : round2(allRatings.stream()
                        .filter(r -> r.getRate() != null)
                        .mapToInt(Rating::getRate)
                        .average()
                        .orElse(0.0));

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

        // 2. Account Summary
        List<User> users = userRepository.findAllByIsDeletedFalse();
        long totalUsers = users.size();
        long activeUsers = users.stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).count();
        long blockedUsers = users.stream().filter(u -> u.getStatus() == UserStatus.BLOCKED).count();
        long residentCount = countUsersByRole(users, "RESIDENT");
        long staffCount = countUsersByRole(users, "STAFF");
        long managerCount = countUsersByRole(users, "MANAGER");
        long adminCount = countUsersByRole(users, "ADMIN");

        AccountKpiSummary accountSummary = AccountKpiSummary.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .blockedUsers(blockedUsers)
                .residentCount(residentCount)
                .staffCount(staffCount)
                .managerCount(managerCount)
                .adminCount(adminCount)
                .build();

        // 3. Config Summary
        List<Department> activeDepartments = departmentRepository.findAll().stream()
                .filter(d -> d != null && !d.isDeleted())
                .toList();
        List<Category> categories = categoryRepository.findAllByIsDeletedFalse();
        long activeCategoriesCount = categories.stream().filter(Category::isActive).count();
        List<SubCategory> subCategories = subCategoryRepository.findByIsDeletedFalse();
        long activeSubCategoriesCount = subCategories.stream().filter(SubCategory::isActive).count();
        List<Step> steps = stepRepository.findByIsDeletedFalse();
        long totalRoutingRules = routingRuleRepository.findAll().stream()
                .filter(r -> r != null && !r.isDeleted())
                .count();

        ConfigKpiSummary configSummary = ConfigKpiSummary.builder()
                .totalDepartments(activeDepartments.size())
                .totalCategories(categories.size())
                .activeCategories(activeCategoriesCount)
                .totalSubCategories(subCategories.size())
                .activeSubCategories(activeSubCategoriesCount)
                .totalSteps(steps.size())
                .totalRoutingRules(totalRoutingRules)
                .build();

        // 4. Department Summaries
        List<DepartmentKpiItem> departmentSummaries = buildDepartmentKpiItems(activeDepartments, allTickets, now);

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

        List<Ticket> tickets = ticketRepository.findAll(TicketSpecification.withFilter(filter));

        Set<UUID> ticketIds = tickets.stream()
                .map(Ticket::getTicketId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, List<Rating>> ratingsByTicket = ratingRepository.findAll().stream()
                .filter(r -> r != null && !r.isDeleted() && r.getTicket() != null && ticketIds.contains(r.getTicket().getTicketId()))
                .collect(Collectors.groupingBy(r -> r.getTicket().getTicketId()));

        // 1. Overview
        AnalyticsOverview overview = buildAnalyticsOverview(tickets, ratingsByTicket, now);

        // 2. Time Series
        List<TimeSeriesItem> timeSeries = buildTimeSeries(tickets, groupBy);

        // 3. Geographic Breakdown
        List<GeographicAnalyticsItem> geographicBreakdown = buildGeographicBreakdown(tickets, now);

        // 4. Department Breakdown
        List<Department> departments = departmentRepository.findAll().stream()
                .filter(d -> d != null && !d.isDeleted())
                .filter(d -> departmentId == null || departmentId.equals(d.getDepartmentId()))
                .toList();
        List<DepartmentAnalyticsItem> departmentBreakdown = buildDepartmentBreakdown(departments, tickets, ratingsByTicket, now);

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

    private AnalyticsOverview buildAnalyticsOverview(
            List<Ticket> tickets,
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

        long onTimeCount = resolvedTicketsList.stream()
                .filter(this::isResolvedOnTime)
                .count();

        double onTimeResolutionRate = !resolvedTicketsList.isEmpty()
                ? round2(((double) onTimeCount / resolvedTicketsList.size()) * 100.0)
                : 0.0;

        double avgResolutionHours = calculateAverageResolutionHours(resolvedTicketsList);

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

    private List<DepartmentKpiItem> buildDepartmentKpiItems(
            List<Department> departments,
            List<Ticket> allTickets,
            LocalDateTime now) {

        List<DepartmentKpiItem> items = new ArrayList<>();
        for (Department dept : departments) {
            UUID deptId = dept.getDepartmentId();
            List<Ticket> deptTickets = allTickets.stream()
                    .filter(t -> isTicketAssignedToDepartment(t, deptId))
                    .toList();

            long total = deptTickets.size();
            long inProgress = deptTickets.stream()
                    .filter(t -> t.getStatus() == TicketStatus.ASSIGNED || t.getStatus() == TicketStatus.IN_PROGRESS)
                    .count();
            long resolvedOrCompleted = deptTickets.stream()
                    .filter(t -> isResolvedOrCompleted(t.getStatus()))
                    .count();
            long overdue = deptTickets.stream()
                    .filter(t -> isTicketOverdue(t, now))
                    .count();
            long valid = deptTickets.stream()
                    .filter(t -> t.getStatus() != TicketStatus.REJECTED && t.getStatus() != TicketStatus.CANCELLED)
                    .count();

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

    private List<DepartmentAnalyticsItem> buildDepartmentBreakdown(
            List<Department> departments,
            List<Ticket> tickets,
            Map<UUID, List<Rating>> ratingsByTicket,
            LocalDateTime now) {

        List<DepartmentAnalyticsItem> items = new ArrayList<>();
        for (Department dept : departments) {
            UUID deptId = dept.getDepartmentId();
            List<Ticket> deptTickets = tickets.stream()
                    .filter(t -> isTicketAssignedToDepartment(t, deptId))
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

            long onTime = resolvedList.stream().filter(this::isResolvedOnTime).count();
            double onTimeRate = !resolvedList.isEmpty() ? round2(((double) onTime / resolvedList.size()) * 100.0) : 0.0;

            double avgHours = calculateAverageResolutionHours(resolvedList);

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

    private boolean isTicketUnassigned(Ticket ticket) {
        if (ticket == null) {
            return false;
        }
        if (ticket.getSteps() == null || ticket.getSteps().isEmpty()) {
            return true;
        }
        return ticket.getSteps().stream()
                .noneMatch(s -> s != null && !s.isDeleted() && s.getAssignee() != null);
    }

    private boolean isTicketAssignedToDepartment(Ticket ticket, UUID departmentId) {
        if (ticket == null || departmentId == null || ticket.getSteps() == null) {
            return false;
        }
        for (TicketStep step : ticket.getSteps()) {
            if (step != null && !step.isDeleted() && step.getAssignee() != null) {
                User assignee = step.getAssignee();
                if (assignee.getDepartment() != null && departmentId.equals(assignee.getDepartment().getDepartmentId())) {
                    return true;
                }
                if (assignee.getUserDepartments() != null) {
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

    private boolean isResolvedOnTime(Ticket ticket) {
        if (ticket == null) {
            return false;
        }
        if (ticket.getDeadline() == null) {
            return true;
        }
        LocalDateTime resolvedTime = resolveCompletionTime(ticket);
        if (resolvedTime == null) {
            return true;
        }
        return !resolvedTime.isAfter(ticket.getDeadline());
    }

    private LocalDateTime resolveCompletionTime(Ticket ticket) {
        if (ticket.getSteps() != null && !ticket.getSteps().isEmpty()) {
            Optional<LocalDateTime> maxStepCompletion = ticket.getSteps().stream()
                    .filter(s -> s != null && !s.isDeleted() && s.getCompletedAt() != null)
                    .map(TicketStep::getCompletedAt)
                    .max(LocalDateTime::compareTo);
            if (maxStepCompletion.isPresent()) {
                return maxStepCompletion.get();
            }
        }
        return ticket.getUpdatedAt();
    }

    private double calculateAverageResolutionHours(List<Ticket> resolvedTickets) {
        if (resolvedTickets == null || resolvedTickets.isEmpty()) {
            return 0.0;
        }
        List<Double> hoursList = new ArrayList<>();
        for (Ticket t : resolvedTickets) {
            LocalDateTime start = t.getCreatedAt();
            LocalDateTime end = resolveCompletionTime(t);
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

    private boolean isTerminalNonActiveStatus(TicketStatus status) {
        return status == TicketStatus.COMPLETED || status == TicketStatus.REJECTED || status == TicketStatus.CANCELLED;
    }

    private long countByStatus(List<Ticket> tickets, TicketStatus status) {
        return tickets.stream().filter(t -> t.getStatus() == status).count();
    }

    private long countUsersByRole(List<User> users, String roleName) {
        return users.stream()
                .filter(u -> u.getRole() != null && roleName.equalsIgnoreCase(u.getRole().getRoleName()))
                .count();
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
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
