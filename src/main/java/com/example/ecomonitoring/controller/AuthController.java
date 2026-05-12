package com.example.ecomonitoring.controller;

import com.example.ecomonitoring.dto.AuthResponse;
import com.example.ecomonitoring.dto.LoginRequest;
import com.example.ecomonitoring.dto.RegisterRequest;
import com.example.ecomonitoring.entity.User;
import com.example.ecomonitoring.repository.UserRepository;
import com.example.ecomonitoring.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email уже зарегистрирован"));
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setMessengerId(request.getMessengerId());
        user.setCreatedAt(LocalDateTime.now());

        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail(), "USER");
        return ResponseEntity.ok(new AuthResponse(token, user.getEmail(), "USER"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Неверный email или пароль"));
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(new AuthResponse(token, user.getEmail(), user.getRole().name()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String token) {
        try {
            String email = jwtService.extractEmail(token.substring(7));
            User user = userRepository.findByEmail(email).orElseThrow();

            Map<String, Object> response = new HashMap<>();
            response.put("email", user.getEmail());
            response.put("messengerId", user.getMessengerId());
            response.put("role", user.getRole());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Не авторизован"));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestHeader("Authorization") String token,
                                           @RequestBody Map<String, String> updates) {
        try {
            String email = jwtService.extractEmail(token.substring(7));
            User user = userRepository.findByEmail(email).orElseThrow();

            if (updates.containsKey("email") && !updates.get("email").equals(user.getEmail())) {
                if (userRepository.existsByEmail(updates.get("email"))) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Email уже используется"));
                }
                user.setEmail(updates.get("email"));
            }

            if (updates.containsKey("messengerId")) {
                user.setMessengerId(updates.get("messengerId"));
            }

            if (updates.containsKey("password") && !updates.get("password").isEmpty()) {
                user.setPassword(passwordEncoder.encode(updates.get("password")));
            }

            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Профиль обновлен"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }
}