package com.cityfix.backend.service.impl;

import com.cityfix.backend.model.dto.response.LocationBoundary;
import com.cityfix.backend.repository.AdministrativeBoundaryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceImplTest {

    @Mock
    private AdministrativeBoundaryRepository administrativeBoundaryRepository;

    @InjectMocks
    private LocationServiceImpl locationService;

    @Test
    @DisplayName("getBoundaryFromCoordinates_validCoordinates_returnsLocationBoundary")
    void getBoundaryFromCoordinates_validCoordinates_returnsLocationBoundary() {
        AdministrativeBoundaryRepository.LocationBoundaryProjection projection = new AdministrativeBoundaryRepository.LocationBoundaryProjection() {
            @Override
            public String getProvince() {
                return "Thành phố Hồ Chí Minh";
            }

            @Override
            public String getWard() {
                return "Phường Bến Nghé";
            }
        };

        when(administrativeBoundaryRepository.findBoundaryByCoordinates(106.700980, 10.776530))
                .thenReturn(Optional.of(projection));

        LocationBoundary result = locationService.getBoundaryFromCoordinates(10.776530, 106.700980);

        assertThat(result).isNotNull();
        assertThat(result.getProvince()).isEqualTo("Thành phố Hồ Chí Minh");
        assertThat(result.getWard()).isEqualTo("Phường Bến Nghé");
    }

    @Test
    @DisplayName("getBoundaryFromCoordinates_nullCoordinates_returnsNull")
    void getBoundaryFromCoordinates_nullCoordinates_returnsNull() {
        assertThat(locationService.getBoundaryFromCoordinates(null, 106.0)).isNull();
        assertThat(locationService.getBoundaryFromCoordinates(10.0, null)).isNull();
        assertThat(locationService.getBoundaryFromCoordinates(null, null)).isNull();

        verifyNoInteractions(administrativeBoundaryRepository);
    }

    @Test
    @DisplayName("getBoundaryFromCoordinates_notFound_returnsNull")
    void getBoundaryFromCoordinates_notFound_returnsNull() {
        when(administrativeBoundaryRepository.findBoundaryByCoordinates(106.0, 10.0))
                .thenReturn(Optional.empty());

        LocationBoundary result = locationService.getBoundaryFromCoordinates(10.0, 106.0);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getBoundaryFromCoordinates_repositoryThrowsException_returnsNullGracefully")
    void getBoundaryFromCoordinates_repositoryThrowsException_returnsNullGracefully() {
        when(administrativeBoundaryRepository.findBoundaryByCoordinates(any(), any()))
                .thenThrow(new RuntimeException("Database error"));

        LocationBoundary result = locationService.getBoundaryFromCoordinates(10.0, 106.0);

        assertThat(result).isNull();
    }
}
