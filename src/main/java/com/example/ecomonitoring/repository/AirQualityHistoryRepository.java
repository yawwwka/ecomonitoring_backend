package com.example.ecomonitoring.repository;

import com.example.ecomonitoring.entity.AirQualityHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AirQualityHistoryRepository extends JpaRepository<AirQualityHistory, Long> {

    List<AirQualityHistory> findByCityIdOrderByRequestedAtDesc(Long cityId);

    List<AirQualityHistory> findByCityId(Long cityId);

    List<AirQualityHistory> findByCityIdAndRequestedAtAfter(Long cityId, LocalDateTime date);
}