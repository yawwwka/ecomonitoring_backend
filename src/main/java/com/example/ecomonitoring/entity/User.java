package com.example.ecomonitoring.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(name = "telegram_id")
    private String telegramId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Location> locations = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<NotificationSetting> notificationSettings = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AirQualityHistory> airQualityHistories = new ArrayList<>();

    // Конструкторы
    public User() {}

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = LocalDateTime.now();
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getTelegramId() { return telegramId; }
    public void setTelegramId(String telegramId) { this.telegramId = telegramId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Location> getLocations() { return locations; }
    public void setLocations(List<Location> locations) { this.locations = locations; }

    public List<NotificationSetting> getNotificationSettings() { return notificationSettings; }
    public void setNotificationSettings(List<NotificationSetting> notificationSettings) { this.notificationSettings = notificationSettings; }

    public List<AirQualityHistory> getAirQualityHistories() { return airQualityHistories; }
    public void setAirQualityHistories(List<AirQualityHistory> airQualityHistories) { this.airQualityHistories = airQualityHistories; }
}