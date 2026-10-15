package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.AdministrativeBoundary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdministrativeBoundaryRepository extends JpaRepository<AdministrativeBoundary, Long> {

    @Query(value = "SELECT province, ward FROM administrative_boundary WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)) LIMIT 1", nativeQuery = true)
    Optional<LocationBoundaryProjection> findBoundaryByCoordinates(
            @Param("longitude") Double longitude,
            @Param("latitude") Double latitude
    );

    interface LocationBoundaryProjection {
        String getProvince();
        String getWard();
    }
}
