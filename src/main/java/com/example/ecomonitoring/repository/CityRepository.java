package com.example.ecomonitoring.repository;

import com.example.ecomonitoring.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    @Query(value = """
    SELECT id, name, latitude, longitude, population, region, federal_district, timezone
    FROM cities
    WHERE name ILIKE CONCAT('%', :query, '%')
    ORDER BY 
        CASE 
            WHEN name ILIKE :query || '%' THEN 0 
            WHEN name ILIKE CONCAT('%', :query, '%') THEN 1 
            ELSE 2 
        END,
        population DESC NULLS LAST
    LIMIT 10
    """, nativeQuery = true)
    List<City> searchByName(@Param("query") String query);

    Optional<City> findByNameIgnoreCase(String name);
}