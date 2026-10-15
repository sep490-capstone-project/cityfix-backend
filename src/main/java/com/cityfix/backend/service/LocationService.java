package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.response.LocationBoundary;

public interface LocationService {
    LocationBoundary getBoundaryFromCoordinates(Double latitude, Double longitude);
}
