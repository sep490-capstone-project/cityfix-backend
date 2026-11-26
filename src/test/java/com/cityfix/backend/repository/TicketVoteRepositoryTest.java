package com.cityfix.backend.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketVoteRepositoryTest {

    @Test
    @DisplayName("findByTicketIdWithUserOrderByCreatedAtDesc_filtersActiveNonDeletedUsers")
    void findByTicketIdWithUserOrderByCreatedAtDesc_filtersActiveNonDeletedUsers() throws Exception {
        Method method = TicketVoteRepository.class.getMethod("findByTicketIdWithUserOrderByCreatedAtDesc", UUID.class);
        Query queryAnnotation = method.getAnnotation(Query.class);

        assertThat(queryAnnotation).isNotNull();
        String jpql = queryAnnotation.value();
        assertThat(jpql)
                .contains("JOIN FETCH tv.user u")
                .contains("LEFT JOIN FETCH u.userProfile")
                .contains("u.isDeleted = false")
                .contains("u.status = com.cityfix.backend.model.enums.UserStatus.ACTIVE")
                .contains("ORDER BY tv.createdAt DESC");
    }
}
