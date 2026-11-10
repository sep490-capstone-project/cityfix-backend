package com.cityfix.backend.repository.specification;

import com.cityfix.backend.model.dto.request.AdminTicketFilterRequest;
import com.cityfix.backend.model.entity.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TicketSpecification {

    private TicketSpecification() {
    }

    public static Specification<Ticket> withFilter(AdminTicketFilterRequest filter) {
        return (root, query, cb) -> {
            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            // 1. Keyword search (description, address, creator fullName/email/phoneNumber, ticketId)
            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String kw = "%" + filter.getKeyword().trim().toLowerCase() + "%";
                Join<Ticket, User> userJoin = root.join("user", JoinType.LEFT);

                List<Predicate> keywordPredicates = new ArrayList<>();
                keywordPredicates.add(cb.like(cb.lower(root.get("description")), kw));
                keywordPredicates.add(cb.like(cb.lower(cb.coalesce(root.get("address"), "")), kw));
                keywordPredicates.add(cb.like(cb.lower(cb.coalesce(userJoin.get("fullName"), "")), kw));
                keywordPredicates.add(cb.like(cb.lower(cb.coalesce(userJoin.get("email"), "")), kw));
                keywordPredicates.add(cb.like(cb.lower(cb.coalesce(userJoin.get("phoneNumber"), "")), kw));

                try {
                    UUID parsedId = UUID.fromString(filter.getKeyword().trim());
                    keywordPredicates.add(cb.equal(root.get("ticketId"), parsedId));
                } catch (IllegalArgumentException ignored) {
                    // Not a full UUID string
                }

                predicates.add(cb.or(keywordPredicates.toArray(new Predicate[0])));
            }

            // 2. Status
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            // 3. Priority
            if (filter.getPriority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.getPriority()));
            }

            // 4. CategoryId
            if (filter.getCategoryId() != null && query != null) {
                Subquery<Integer> catSubquery = query.subquery(Integer.class);
                Root<TicketSubCategory> tscRoot = catSubquery.from(TicketSubCategory.class);
                catSubquery.select(cb.literal(1))
                        .where(
                                cb.equal(tscRoot.get("ticket"), root),
                                cb.equal(tscRoot.get("subCategory").get("category").get("categoryId"), filter.getCategoryId())
                        );
                predicates.add(cb.exists(catSubquery));
            }

            // 5. SubCategoryId
            if (filter.getSubCategoryId() != null && query != null) {
                Subquery<Integer> subCatSubquery = query.subquery(Integer.class);
                Root<TicketSubCategory> tscRoot = subCatSubquery.from(TicketSubCategory.class);
                subCatSubquery.select(cb.literal(1))
                        .where(
                                cb.equal(tscRoot.get("ticket"), root),
                                cb.equal(tscRoot.get("subCategory").get("subCategoryId"), filter.getSubCategoryId())
                        );
                predicates.add(cb.exists(subCatSubquery));
            }

            // 6. DepartmentId (assigned via TicketStep or UserDepartment, or fallback via RoutingRule for unassigned tickets)
            if (filter.getDepartmentId() != null && query != null) {
                UUID deptId = filter.getDepartmentId();

                // 6a. Assigned directly via TicketStep.assignee.department or UserDepartment
                Subquery<Integer> stepDeptSubquery = query.subquery(Integer.class);
                Root<TicketStep> tsRoot = stepDeptSubquery.from(TicketStep.class);
                Join<TicketStep, User> assigneeJoin = tsRoot.join("assignee", JoinType.INNER);
                Join<User, Department> directDeptJoin = assigneeJoin.join("department", JoinType.LEFT);

                Subquery<Integer> udSubquery = stepDeptSubquery.subquery(Integer.class);
                Root<UserDepartment> udRoot = udSubquery.from(UserDepartment.class);
                udSubquery.select(cb.literal(1))
                        .where(
                                cb.equal(udRoot.get("user"), assigneeJoin),
                                cb.equal(udRoot.get("department").get("departmentId"), deptId),
                                cb.isFalse(udRoot.get("department").get("isDeleted"))
                        );

                stepDeptSubquery.select(cb.literal(1))
                        .where(
                                cb.equal(tsRoot.get("ticket"), root),
                                cb.isFalse(tsRoot.get("isDeleted")),
                                cb.or(
                                        cb.equal(directDeptJoin.get("departmentId"), deptId),
                                        cb.exists(udSubquery)
                                )
                        );

                // 6b. Fallback via RoutingRule when ticket has no active assigned TicketStep
                Subquery<Integer> anyAssignedStepSubquery = query.subquery(Integer.class);
                Root<TicketStep> tsAssignedRoot = anyAssignedStepSubquery.from(TicketStep.class);
                anyAssignedStepSubquery.select(cb.literal(1))
                        .where(
                                cb.equal(tsAssignedRoot.get("ticket"), root),
                                cb.isFalse(tsAssignedRoot.get("isDeleted")),
                                cb.isNotNull(tsAssignedRoot.get("assignee"))
                        );

                Subquery<Integer> routingRuleSubquery = query.subquery(Integer.class);
                Root<RoutingRule> rrRoot = routingRuleSubquery.from(RoutingRule.class);
                Root<TicketSubCategory> tscRoot = routingRuleSubquery.from(TicketSubCategory.class);

                Expression<String> lowerAddress = cb.lower(cb.coalesce(root.get("address"), ""));
                Predicate provinceMatch = cb.or(
                        cb.isNull(rrRoot.get("province")),
                        cb.equal(cb.trim(rrRoot.get("province")), ""),
                        cb.like(lowerAddress, cb.concat(cb.concat("%", cb.lower(rrRoot.get("province"))), "%"))
                );
                Predicate wardMatch = cb.or(
                        cb.isNull(rrRoot.get("ward")),
                        cb.equal(cb.trim(rrRoot.get("ward")), ""),
                        cb.like(lowerAddress, cb.concat(cb.concat("%", cb.lower(rrRoot.get("ward"))), "%"))
                );

                routingRuleSubquery.select(cb.literal(1))
                        .where(
                                cb.equal(tscRoot.get("ticket"), root),
                                cb.equal(rrRoot.get("category"), tscRoot.get("subCategory").get("category")),
                                cb.equal(rrRoot.get("department").get("departmentId"), deptId),
                                cb.isFalse(rrRoot.get("isDeleted")),
                                provinceMatch,
                                wardMatch
                        );

                Predicate unassignedAndRoutedToDept = cb.and(
                        cb.not(cb.exists(anyAssignedStepSubquery)),
                        cb.exists(routingRuleSubquery)
                );

                predicates.add(cb.or(cb.exists(stepDeptSubquery), unassignedAndRoutedToDept));
            }

            // 7. Unassigned flag (true = no active TicketStep with non-null assignee; false = has at least one assigned TicketStep)
            if (filter.getUnassigned() != null && query != null) {
                Subquery<Integer> assignedSubquery = query.subquery(Integer.class);
                Root<TicketStep> tsRoot = assignedSubquery.from(TicketStep.class);
                assignedSubquery.select(cb.literal(1))
                        .where(
                                cb.equal(tsRoot.get("ticket"), root),
                                cb.isFalse(tsRoot.get("isDeleted")),
                                cb.isNotNull(tsRoot.get("assignee"))
                        );
                if (Boolean.TRUE.equals(filter.getUnassigned())) {
                    predicates.add(cb.not(cb.exists(assignedSubquery)));
                } else {
                    predicates.add(cb.exists(assignedSubquery));
                }
            }

            // 8. Province filter
            if (filter.getProvince() != null && !filter.getProvince().isBlank()) {
                String prov = "%" + filter.getProvince().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("address"), "")), prov));
            }

            // 9. Ward filter
            if (filter.getWard() != null && !filter.getWard().isBlank()) {
                String wardPattern = "%" + filter.getWard().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("address"), "")), wardPattern));
            }

            // 10. isLifeThreatening
            if (filter.getIsLifeThreatening() != null) {
                predicates.add(cb.equal(root.get("isLifeThreatening"), filter.getIsLifeThreatening()));
            }

            // 11. needsManualReview
            if (filter.getNeedsManualReview() != null) {
                predicates.add(cb.equal(root.get("needsManualReview"), filter.getNeedsManualReview()));
            }

            // 12. Date range (fromDate, toDate)
            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFromDate()));
            }
            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getToDate()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
