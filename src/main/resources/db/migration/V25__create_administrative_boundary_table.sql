-- Migration V25: Tao bang administrative_boundary phuc vu PostGIS Reverse Geocoding
CREATE EXTENSION IF NOT EXISTS "postgis";

CREATE TABLE IF NOT EXISTS administrative_boundary (
    id BIGSERIAL PRIMARY KEY,
    province VARCHAR(255),
    ward VARCHAR(255),
    geom geometry(Geometry, 4326)
);

CREATE INDEX IF NOT EXISTS idx_administrative_boundary_geom ON administrative_boundary USING GIST (geom);
