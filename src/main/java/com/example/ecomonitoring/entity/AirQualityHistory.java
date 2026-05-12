package com.example.ecomonitoring.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "air_quality_history")
public class AirQualityHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "city_id")
    private City city;

    private Double latitude;
    private Double longitude;
    private Double pm25;
    private Double pm10;
    private Double co;
    private Double no2;
    private Double so2;
    private Integer aqi;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    // Конструкторы
    public AirQualityHistory() {}

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public City getCity() { return city; }
    public void setCity(City city) { this.city = city; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getPm25() { return pm25; }
    public void setPm25(Double pm25) { this.pm25 = pm25; }

    public Double getPm10() { return pm10; }
    public void setPm10(Double pm10) { this.pm10 = pm10; }

    public Double getCo() { return co; }
    public void setCo(Double co) { this.co = co; }

    public Double getNo2() { return no2; }
    public void setNo2(Double no2) { this.no2 = no2; }

    public Double getSo2() { return so2; }
    public void setSo2(Double so2) { this.so2 = so2; }

    public Integer getAqi() { return aqi; }
    public void setAqi(Integer aqi) { this.aqi = aqi; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
}