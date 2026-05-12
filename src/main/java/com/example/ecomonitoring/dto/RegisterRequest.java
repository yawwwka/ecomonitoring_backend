package com.example.ecomonitoring.dto;

public class RegisterRequest {
    private String email;
    private String password;
    private String telegramId;

    // Геттеры и сеттеры
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTelegramId() { return telegramId; }
    public void setTelegramId(String telegramId) { this.telegramId = telegramId; }
}