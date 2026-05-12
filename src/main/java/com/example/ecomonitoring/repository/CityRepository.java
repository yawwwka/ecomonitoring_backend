package com.example.ecomonitoring.repository;

import com.example.ecomonitoring.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {

    @Query(value = """
        SELECT id, name, latitude, longitude, population, region, federal_district, timezone
        FROM cities
        ORDER BY ST_Distance(
            ST_MakePoint(longitude, latitude)::geography,
            ST_MakePoint(:lon, :lat)::geography
        )
        LIMIT 1
        """, nativeQuery = true)
    City findNearest(@Param("lat") double lat, @Param("lon") double lon);
}