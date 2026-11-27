package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.RoutingRule;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.TicketSubCategory;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.UserDepartment;
import com.cityfix.backend.repository.RoutingRuleRepository;
import com.cityfix.backend.repository.TicketStepRepository;
import com.cityfix.backend.repository.UserDepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ManagerDepartmentAccessValidator {

    private final UserDepartmentRepository userDepartmentRepository;
    private final TicketStepRepository ticketStepRepository;
    private final RoutingRuleRepository routingRuleRepository;

    public Set<UUID> requireManagedDepartmentIds(User manager) {
        Set<UUID> deptIds = resolveUserDepartmentIds(manager);
        if (deptIds.isEmpty()) {
            throw new AppException(ErrorCode.USER_NOT_IN_DEPARTMENT);
        }
        return deptIds;
    }

    public void validateTicketAccess(User manager, Ticket ticket) {
        Set<UUID> managerDeptIds = requireManagedDepartmentIds(manager);
        if (ticket == null) {
            throw new AppException(ErrorCode.TICKET_NOT_FOUND);
        }

        if (!isTicketInManagedDepartments(ticket, managerDeptIds)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Vé không thuộc phòng ban bạn quản lý.");
        }
    }

    public void validateStepAccess(User manager, TicketStep step, boolean requireAssigneeDepartmentMatch) {
        Set<UUID> managerDeptIds = requireManagedDepartmentIds(manager);
        if (step == null) {
            throw new AppException(ErrorCode.STEP_NOT_FOUND);
        }

        if (requireAssigneeDepartmentMatch) {
            if (step.getAssignee() == null) {
                throw new AppException(ErrorCode.FORBIDDEN, "Bước xử lý chưa được phân công nhân viên thuộc phòng ban bạn quản lý.");
            }
            Set<UUID> assigneeDeptIds = resolveUserDepartmentIds(step.getAssignee());
            boolean belongsToManagerDept = assigneeDeptIds.stream().anyMatch(managerDeptIds::contains);
            if (!belongsToManagerDept) {
                throw new AppException(ErrorCode.FORBIDDEN, "Bước xử lý không thuộc phòng ban bạn quản lý.");
            }
            return;
        }

        Ticket ticket = step.getTicket();
        if (ticket != null) {
            if (!isTicketInManagedDepartments(ticket, managerDeptIds)) {
                throw new AppException(ErrorCode.FORBIDDEN, "Vé không thuộc phòng ban bạn quản lý.");
            }
            return;
        }

        // Fallback when step has no ticket reference loaded
        if (step.getAssignee() != null) {
            Set<UUID> assigneeDeptIds = resolveUserDepartmentIds(step.getAssignee());
            if (assigneeDeptIds.stream().anyMatch(managerDeptIds::contains)) {
                return;
            }
        }
        throw new AppException(ErrorCode.FORBIDDEN, "Bước xử lý không thuộc phòng ban bạn quản lý.");
    }

    private boolean isTicketInManagedDepartments(Ticket ticket, Set<UUID> managerDeptIds) {
        List<TicketStep> steps = null;
        if (ticket.getTicketId() != null && ticketStepRepository != null) {
            steps = ticketStepRepository.findByTicketTicketId(ticket.getTicketId());
        }
        if (steps == null || steps.isEmpty()) {
            try {
                steps = ticket.getSteps();
            } catch (Exception ignored) {
                steps = Collections.emptyList();
            }
        }

        List<TicketStep> activeAssignedSteps = (steps == null ? Collections.<TicketStep>emptyList() : steps).stream()
                .filter(Objects::nonNull)
                .filter(s -> !s.isDeleted() && s.getAssignee() != null)
                .toList();

        if (!activeAssignedSteps.isEmpty()) {
            for (TicketStep step : activeAssignedSteps) {
                Set<UUID> assigneeDeptIds = resolveUserDepartmentIds(step.getAssignee());
                if (assigneeDeptIds.stream().anyMatch(managerDeptIds::contains)) {
                    return true;
                }
            }
            return false;
        }

        // Unassigned ticket (e.g., INITIATED): match against active RoutingRule by category + address
        return matchesRoutingRuleForDepartments(ticket, managerDeptIds);
    }

    private boolean matchesRoutingRuleForDepartments(Ticket ticket, Set<UUID> managerDeptIds) {
        Set<UUID> categoryIds = extractCategoryIds(ticket);
        if (categoryIds.isEmpty() || routingRuleRepository == null) {
            return false;
        }

        List<RoutingRule> rules = routingRuleRepository.findByCategoryCategoryIdInAndIsDeletedFalse(categoryIds);
        if (rules == null || rules.isEmpty()) {
            return false;
        }

        String rawAddress = ticket.getAddress();
        String lowerAddress = rawAddress != null ? rawAddress.trim().replaceAll("\\s+", " ").toLowerCase() : "";

        for (RoutingRule rule : rules) {
            if (rule == null || rule.isDeleted()) {
                continue;
            }
            Department dept = rule.getDepartment();
            if (dept == null || dept.isDeleted() || dept.getDepartmentId() == null) {
                continue;
            }
            if (!managerDeptIds.contains(dept.getDepartmentId())) {
                continue;
            }

            if (matchesLocation(rule.getProvince(), "PROVINCE", lowerAddress)
                    && matchesLocation(rule.getWard(), "WARD", lowerAddress)) {
                return true;
            }
        }
        return false;
    }

    private Set<UUID> extractCategoryIds(Ticket ticket) {
        if (ticket == null) {
            return Collections.emptySet();
        }
        Set<UUID> categoryIds = new LinkedHashSet<>();
        try {
            List<TicketSubCategory> subCategories = ticket.getSubCategories();
            if (subCategories != null) {
                for (TicketSubCategory tsc : subCategories) {
                    if (tsc != null
                            && tsc.getSubCategory() != null
                            && tsc.getSubCategory().getCategory() != null
                            && tsc.getSubCategory().getCategory().getCategoryId() != null) {
                        categoryIds.add(tsc.getSubCategory().getCategory().getCategoryId());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return categoryIds;
    }

    private boolean matchesLocation(String ruleLocation, String type, String lowerAddress) {
        if (ruleLocation == null || ruleLocation.isBlank()) {
            return true;
        }
        if (lowerAddress == null || lowerAddress.isEmpty()) {
            return false;
        }
        String trimmedRule = ruleLocation.trim().toLowerCase();
        if (lowerAddress.contains(trimmedRule)) {
            return true;
        }
        String normalized = normalizeLocationName(ruleLocation, type);
        return normalized != null && !normalized.isEmpty() && lowerAddress.contains(normalized.toLowerCase());
    }

    private String normalizeLocationName(String rawName, String type) {
        if (rawName == null) {
            return null;
        }
        String cleaned = rawName.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return null;
        }
        if ("PROVINCE".equalsIgnoreCase(type)) {
            cleaned = cleaned.replaceFirst("(?iu)^(thành\\s+phố\\s+|tỉnh\\s+|tp\\.\\s*|tp\\s+)", "").trim();
            if ("HCM".equalsIgnoreCase(cleaned) || "TPHCM".equalsIgnoreCase(cleaned)) {
                cleaned = "Hồ Chí Minh";
            }
        } else if ("WARD".equalsIgnoreCase(type)) {
            cleaned = cleaned.replaceFirst("(?iu)^(phường\\s+|p\\.\\s*|xã\\s+|thị\\s+trấn\\s+|tt\\.\\s*)", "").trim();
        } else {
            cleaned = cleaned.replaceFirst("(?iu)^(thành\\s+phố\\s+|tỉnh\\s+|tp\\.\\s*|tp\\s+|phường\\s+|p\\.\\s*|xã\\s+|thị\\s+trấn\\s+|tt\\.\\s*)", "").trim();
        }
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private Set<UUID> resolveUserDepartmentIds(User user) {
        if (user == null) {
            return Collections.emptySet();
        }
        Set<UUID> deptIds = new LinkedHashSet<>();
        if (user.getUserId() != null && userDepartmentRepository != null) {
            List<Department> fromRepo = userDepartmentRepository.findDepartmentsByUserId(user.getUserId());
            if (fromRepo != null) {
                for (Department d : fromRepo) {
                    if (d != null && !d.isDeleted() && d.getDepartmentId() != null) {
                        deptIds.add(d.getDepartmentId());
                    }
                }
            }
        }
        if (user.getUserDepartments() != null) {
            try {
                for (UserDepartment ud : user.getUserDepartments()) {
                    if (ud != null && ud.getDepartment() != null && !ud.getDepartment().isDeleted()
                            && ud.getDepartment().getDepartmentId() != null) {
                        deptIds.add(ud.getDepartment().getDepartmentId());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return deptIds;
    }
}
