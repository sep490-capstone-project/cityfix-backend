package com.cityfix.backend.repository;

import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketRepositoryTest {

    @Test
    @DisplayName("findInitiatedTicketsByDepartmentIds_queryAndCountQuery_haveBalancedParenthesesAndNormalizedLocationMatching")
    void findInitiatedTicketsByDepartmentIds_queryAndCountQuery_haveBalancedParenthesesAndNormalizedLocationMatching() throws Exception {
        Method method = TicketRepository.class.getMethod(
                "findInitiatedTicketsByDepartmentIds",
                Collection.class,
                Pageable.class
        );
        Query queryAnnotation = method.getAnnotation(Query.class);
        assertThat(queryAnnotation).isNotNull();

        assertBalancedParentheses(queryAnnotation.value());
        assertBalancedParentheses(queryAnnotation.countQuery());

        assertContainsNormalizedRoutingRuleMatchers(queryAnnotation.value());
        assertContainsNormalizedRoutingRuleMatchers(queryAnnotation.countQuery());
    }

    @Test
    @DisplayName("findTicketsByDepartmentAndCriteria_queryAndCountQuery_haveBalancedParenthesesAndNormalizedLocationMatching")
    void findTicketsByDepartmentAndCriteria_queryAndCountQuery_haveBalancedParenthesesAndNormalizedLocationMatching() throws Exception {
        Method method = TicketRepository.class.getMethod(
                "findTicketsByDepartmentAndCriteria",
                UUID.class,
                Priority.class,
                TicketStatus.class,
                Pageable.class
        );
        Query queryAnnotation = method.getAnnotation(Query.class);
        assertThat(queryAnnotation).isNotNull();

        assertBalancedParentheses(queryAnnotation.value());
        assertBalancedParentheses(queryAnnotation.countQuery());

        assertContainsNormalizedRoutingRuleMatchers(queryAnnotation.value());
        assertContainsNormalizedRoutingRuleMatchers(queryAnnotation.countQuery());
    }

    @Test
    @DisplayName("jpqlNormalizedLocationLogic_matchesAbbreviatedAndFullPrefixAddresses")
    void jpqlNormalizedLocationLogic_matchesAbbreviatedAndFullPrefixAddresses() {
        String ticketAddress = "12 Lê Lợi, P. Bến Nghé, TP. Hồ Chí Minh";

        String normalizedProvince = simulateJpqlProvinceNormalization("Thành phố Hồ Chí Minh");
        String normalizedWard = simulateJpqlWardNormalization("Phường Bến Nghé");

        assertThat(normalizedProvince).isEqualTo("hồ chí minh");
        assertThat(normalizedWard).isEqualTo("bến nghé");
        assertThat(ticketAddress.toLowerCase())
                .contains(normalizedProvince)
                .contains(normalizedWard);

        // Vice-versa: RoutingRule has abbreviated prefix, Ticket address has full prefix
        String fullTicketAddress = "45 Nguyễn Huệ, Phường Bến Nghé, Thành phố Hồ Chí Minh";
        String normalizedAbbrevProvince = simulateJpqlProvinceNormalization("TP. Hồ Chí Minh");
        String normalizedAbbrevWard = simulateJpqlWardNormalization("P. Bến Nghé");

        assertThat(normalizedAbbrevProvince).isEqualTo("hồ chí minh");
        assertThat(normalizedAbbrevWard).isEqualTo("bến nghé");
        assertThat(fullTicketAddress.toLowerCase())
                .contains(normalizedAbbrevProvince)
                .contains(normalizedAbbrevWard);

        // Non-matching ward must not match
        String otherWardNormalized = simulateJpqlWardNormalization("Phường Bến Thành");
        assertThat(ticketAddress.toLowerCase()).doesNotContain(otherWardNormalized);
    }

    private void assertBalancedParentheses(String jpql) {
        assertThat(jpql).isNotBlank();
        int depth = 0;
        boolean inSingleQuotes = false;
        for (int i = 0; i < jpql.length(); i++) {
            char c = jpql.charAt(i);
            if (c == '\'') {
                inSingleQuotes = !inSingleQuotes;
            } else if (!inSingleQuotes) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    assertThat(depth)
                            .as("Unexpected closing parenthesis at index %d in JPQL:\n%s", i, jpql)
                            .isGreaterThanOrEqualTo(0);
                }
            }
        }
        assertThat(inSingleQuotes).as("Unclosed single quote in JPQL").isFalse();
        assertThat(depth).as("Unbalanced parentheses in JPQL:\n%s", jpql).isZero();
    }

    private void assertContainsNormalizedRoutingRuleMatchers(String jpql) {
        assertThat(jpql)
                .contains("TRIM(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(TRIM(rr.province)), 'thành phố ', ''), 'tỉnh ', ''), 'tp.', ''), 'tp ', ''))")
                .contains("TRIM(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(TRIM(rr.ward)), 'phường ', ''), 'thị trấn ', ''), 'xã ', ''), 'p.', ''), 'tt.', ''))");
    }

    private String simulateJpqlProvinceNormalization(String rawProvince) {
        return rawProvince.trim().toLowerCase()
                .replace("thành phố ", "")
                .replace("tỉnh ", "")
                .replace("tp.", "")
                .replace("tp ", "")
                .trim();
    }

    private String simulateJpqlWardNormalization(String rawWard) {
        return rawWard.trim().toLowerCase()
                .replace("phường ", "")
                .replace("thị trấn ", "")
                .replace("xã ", "")
                .replace("p.", "")
                .replace("tt.", "")
                .trim();
    }
}

