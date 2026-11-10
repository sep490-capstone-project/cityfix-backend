package com.cityfix.backend.repository.specification;

import com.cityfix.backend.model.dto.request.AdminTicketFilterRequest;
import com.cityfix.backend.model.entity.*;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TicketSpecificationTest {

    @Test
    @DisplayName("withFilter_departmentId_buildsStepSubqueryAndRoutingRuleFallbackSubquery")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void withFilter_departmentId_buildsStepSubqueryAndRoutingRuleFallbackSubquery() {
        UUID deptId = UUID.randomUUID();
        AdminTicketFilterRequest filter = AdminTicketFilterRequest.builder()
                .departmentId(deptId)
                .build();

        Root<Ticket> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        Subquery<Integer> stepDeptSubquery = mock(Subquery.class, RETURNS_DEEP_STUBS);
        Subquery<Integer> udSubquery = mock(Subquery.class, RETURNS_DEEP_STUBS);
        Subquery<Integer> anyAssignedStepSubquery = mock(Subquery.class, RETURNS_DEEP_STUBS);
        Subquery<Integer> routingRuleSubquery = mock(Subquery.class, RETURNS_DEEP_STUBS);

        when(query.subquery(Integer.class))
                .thenReturn(stepDeptSubquery, anyAssignedStepSubquery, routingRuleSubquery);
        when(stepDeptSubquery.subquery(Integer.class)).thenReturn(udSubquery);

        Specification<Ticket> spec = TicketSpecification.withFilter(filter);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        // Verify both TicketStep subquery and RoutingRule fallback subquery were constructed
        verify(stepDeptSubquery).from(TicketStep.class);
        verify(udSubquery).from(UserDepartment.class);
        verify(anyAssignedStepSubquery).from(TicketStep.class);
        verify(routingRuleSubquery).from(RoutingRule.class);
        verify(routingRuleSubquery).from(TicketSubCategory.class);
    }
}
