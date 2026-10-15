package com.cityfix.backend.service.impl;

import com.cityfix.backend.model.dto.response.LocationBoundary;
import com.cityfix.backend.repository.AdministrativeBoundaryRepository;
import com.cityfix.backend.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final AdministrativeBoundaryRepository administrativeBoundaryRepository;

    @Override
    @Transactional(readOnly = true)
    public LocationBoundary getBoundaryFromCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        try {
            return administrativeBoundaryRepository.findBoundaryByCoordinates(longitude, latitude)
                    .map(projection -> LocationBoundary.builder()
                            .province(projection.getProvince())
                            .ward(projection.getWard())
                            .build())
                    .orElse(null);
        } catch (Exception e) {
            log.error("Lỗi khi truy vấn địa giới hành chính từ tọa độ GPS ({}, {}): {}", latitude, longitude, e.getMessage());
            return null;
        }
    }
}
