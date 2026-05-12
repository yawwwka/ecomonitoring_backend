package com.example.ecomonitoring.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_settings")
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "telegram_enabled")
    private boolean telegramEnabled = false;

    @Column(name = "email_enabled")
    private boolean emailEnabled = false;

    @Column(name = "threshold_pm25")
    private Double thresholdPm25 = 35.4;

    @Column(name = "threshold_pm10")
    private Double thresholdPm10 = 50.0;

    // Конструкторы
    public NotificationSetting() {}

    public NotificationSetting(User user) {
        this.user = user;
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public boolean isTelegramEnabled() { return telegramEnabled; }
    public void setTelegramEnabled(boolean telegramEnabled) { this.telegramEnabled = telegramEnabled; }

    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }

    public Double getThresholdPm25() { return thresholdPm25; }
    public void setThresholdPm25(Double thresholdPm25) { this.thresholdPm25 = thresholdPm25; }

    public Double getThresholdPm10() { return thresholdPm10; }
    public void setThresholdPm10(Double thresholdPm10) { this.thresholdPm10 = thresholdPm10; }
}