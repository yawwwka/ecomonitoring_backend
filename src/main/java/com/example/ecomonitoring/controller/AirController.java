package com.example.ecomonitoring.controller;

import com.example.ecomonitoring.entity.AirQualityHistory;
import com.example.ecomonitoring.entity.City;
import com.example.ecomonitoring.repository.AirQualityHistoryRepository;
import com.example.ecomonitoring.repository.CityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/air")
@CrossOrigin(origins = "http://localhost:5173")
public class AirController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private AirQualityHistoryRepository historyRepository;

    private int calculateAQI(double pm25) {
        if (pm25 <= 12.0) return 50;
        if (pm25 <= 35.4) return 100;
        if (pm25 <= 55.4) return 150;
        if (pm25 <= 150.4) return 200;
        return 300;
    }

    @GetMapping
    public String getAirQuality(@RequestParam double lat, @RequestParam double lon) {
        // Находим ближайший город
        City nearest = cityRepository.findNearest(lat, lon);

        if (nearest == null) {
            System.out.println("Город не найден для координат: " + lat + ", " + lon);
        } else {
            System.out.println("Ближайший город: " + nearest.getName());
        }

        // Запрос к API Open-Meteo
        String url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + lat + "&longitude=" + lon + "&hourly=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide";
        String response = restTemplate.getForObject(url, String.class);

        // Сохраняем данные в БД
        try {
            JsonNode json = objectMapper.readTree(response);

            double pm25 = json.path("hourly").path("pm2_5").path(0).asDouble();
            double pm10 = json.path("hourly").path("pm10").path(0).asDouble();
            double co = json.path("hourly").path("carbon_monoxide").path(0).asDouble();
            double no2 = json.path("hourly").path("nitrogen_dioxide").path(0).asDouble();
            double so2 = json.path("hourly").path("sulphur_dioxide").path(0).asDouble();

            int aqi = calculateAQI(pm25);

            AirQualityHistory record = new AirQualityHistory();
            record.setCity(nearest);
            record.setLatitude(lat);
            record.setLongitude(lon);
            record.setPm25(pm25);
            record.setPm10(pm10);
            record.setCo(co);
            record.setNo2(no2);
            record.setSo2(so2);
            record.setAqi(aqi);
            record.setRequestedAt(LocalDateTime.now());

            historyRepository.save(record);

            if (nearest != null) {
                System.out.println("Сохранено для города: " + nearest.getName() +
                        ", PM2.5: " + pm25 + ", AQI: " + aqi);
            } else {
                System.out.println("Сохранено для координат: " + lat + ", " + lon +
                        ", PM2.5: " + pm25 + ", AQI: " + aqi);
            }

        } catch (Exception e) {
            System.err.println("Ошибка сохранения в БД: " + e.getMessage());
        }

        return response;
    }

    @GetMapping("/nearest-city")
    public ResponseEntity<City> getNearestCity(@RequestParam double lat, @RequestParam double lon) {
        City nearest = cityRepository.findNearest(lat, lon);
        if (nearest == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(nearest);
    }

    @GetMapping("/history/{cityId}")
    public ResponseEntity<?> getCityHistory(@PathVariable Long cityId) {
        java.util.List<AirQualityHistory> history = historyRepository.findByCityIdOrderByRequestedAtDesc(cityId);
        return ResponseEntity.ok(history);
    }
}