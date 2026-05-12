package com.example.ecomonitoring.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_city_subscriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "city_id"}))
public class UserCitySubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(name = "last_notification_sent")
    private LocalDateTime lastNotificationSent;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public UserCitySubscription() {}

    public UserCitySubscription(User user, City city) {
        this.user = user;
        this.city = city;
        this.createdAt = LocalDateTime.now();
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public City getCity() { return city; }
    public void setCity(City city) { this.city = city; }

    public LocalDateTime getLastNotificationSent() { return lastNotificationSent; }
    public void setLastNotificationSent(LocalDateTime lastNotificationSent) { this.lastNotificationSent = lastNotificationSent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Column(name = "last_status_was_critical")
    private boolean lastStatusWasCritical = false;

    // Геттер и сеттер
    public boolean isLastStatusWasCritical() { return lastStatusWasCritical; }
    public void setLastStatusWasCritical(boolean lastStatusWasCritical) { this.lastStatusWasCritical = lastStatusWasCritical; }
}