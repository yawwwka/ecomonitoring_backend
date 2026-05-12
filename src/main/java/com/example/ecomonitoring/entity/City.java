package com.example.ecomonitoring.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cities")
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Double latitude;
    private Double longitude;
    private Integer population;
    private String region;

    @Column(name = "federal_district")
    private String federalDistrict;

    private String timezone;

    @OneToMany(mappedBy = "city", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<AirQualityHistory> history = new ArrayList<>();

    // Конструкторы
    public City() {}

    public City(String name, Double latitude, Double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Integer getPopulation() { return population; }
    public void setPopulation(Integer population) { this.population = population; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getFederalDistrict() { return federalDistrict; }
    public void setFederalDistrict(String federalDistrict) { this.federalDistrict = federalDistrict; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public List<AirQualityHistory> getHistory() { return history; }
    public void setHistory(List<AirQualityHistory> history) { this.history = history; }
}