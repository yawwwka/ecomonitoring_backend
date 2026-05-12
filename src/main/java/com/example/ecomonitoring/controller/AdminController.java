package com.example.ecomonitoring.controller;

import com.example.ecomonitoring.entity.AirQualityHistory;
import com.example.ecomonitoring.entity.Role;
import com.example.ecomonitoring.entity.User;
import com.example.ecomonitoring.repository.AirQualityHistoryRepository;
import com.example.ecomonitoring.repository.UserRepository;
import com.example.ecomonitoring.service.JwtService;
import com.example.ecomonitoring.service.ScheduledDataCollectionService;
import com.example.ecomonitoring.service.SettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:5173")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AirQualityHistoryRepository historyRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SettingService settingService;

    @Autowired
    private ScheduledDataCollectionService scheduledDataCollectionService;

    @GetMapping("/settings/interval")
    public ResponseEntity<?> getCollectionInterval() {
        long minutes = settingService.getCollectionIntervalMinutes();
        return ResponseEntity.ok(Map.of("interval_minutes", minutes));
    }

    @PutMapping("/settings/interval")
    public ResponseEntity<?> setCollectionInterval(@RequestBody Map<String, Long> body) {
        Long minutes = body.get("interval_minutes");
        if (minutes == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан интервал"));
        }
        settingService.setCollectionIntervalMinutes(minutes);
        return ResponseEntity.ok(Map.of(
                "message", "Интервал обновлен на " + minutes + " минут. Следующий сбор по новому расписанию",
                "interval_minutes", minutes
        ));
    }

    @GetMapping("/next-collection")
    public ResponseEntity<?> getNextCollectionTime(@RequestHeader("Authorization") String token) {
        if (!isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещен"));
        }

        Date nextTime = scheduledDataCollectionService.getNextExecutionTime();
        if (nextTime != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            return ResponseEntity.ok(Map.of(
                    "next_collection", sdf.format(nextTime),
                    "timestamp", nextTime.getTime()
            ));
        }
        return ResponseEntity.ok(Map.of("message", "Время не определено"));
    }

    private boolean isAdmin(String token) {
        try {
            String tokenValue = token.substring(7);
            String role = jwtService.extractRole(tokenValue);
            return "ADMIN".equals(role);
        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestHeader("Authorization") String token) {
         if (!isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещен"));
        }
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/history")
    public ResponseEntity<?> getAllHistory(@RequestHeader("Authorization") String token) {
        if (!isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещен"));
        }
        return ResponseEntity.ok(historyRepository.findAll());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        if (!isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещен"));
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Пользователь удален"));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> body,
                                            @RequestHeader("Authorization") String token) {
        if (!isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещен"));
        }

        String currentUserEmail = jwtService.extractEmail(token.substring(7));
        User currentUser = userRepository.findByEmail(currentUserEmail).orElse(null);
        User targetUser = userRepository.findById(id).orElse(null);

        if (targetUser == null) {
            return ResponseEntity.notFound().build();
        }

        // Запрещаем менять свою роль
        if (currentUser != null && currentUser.getId().equals(targetUser.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нельзя изменить свою собственную роль"));
        }

        targetUser.setRole(Role.valueOf(body.get("role")));
        userRepository.save(targetUser);
        return ResponseEntity.ok(Map.of("message", "Роль обновлена"));
    }
}