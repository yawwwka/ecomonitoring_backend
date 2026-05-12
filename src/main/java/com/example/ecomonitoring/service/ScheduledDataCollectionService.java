package com.example.ecomonitoring.service;

import com.example.ecomonitoring.entity.AirQualityHistory;
import com.example.ecomonitoring.entity.City;
import com.example.ecomonitoring.entity.UserCitySubscription;
import com.example.ecomonitoring.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.example.ecomonitoring.entity.User;
import com.example.ecomonitoring.repository.UserRepository;
//import com.example.ecomonitoring.service.TelegramBotService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;

@Service
public class ScheduledDataCollectionService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ThreadPoolTaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledTask;
    private Date nextExecutionTime;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private AirQualityHistoryRepository historyRepository;

    @Autowired
    private SystemSettingRepository settingRepository;

    @Autowired
    private VkBotService vkBotService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCitySubscriptionRepository subscriptionRepository;

    @PostConstruct
    public void init() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();
        scheduleTask();
    }

    @PreDestroy
    public void destroy() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }

    private long getCollectionIntervalMinutes() {
        try {
            return settingRepository.findBySettingKey("collection_interval_minutes")
                    .map(s -> Long.parseLong(s.getSettingValue()))
                    .orElse(60L);
        } catch (Exception e) {
            return 60L;
        }
    }

    private String getCronExpression() {
        long minutes = getCollectionIntervalMinutes();
        if (minutes <= 0) minutes = 60;
        if (minutes < 1) minutes = 1;
        return String.format("0 */%d * * * *", minutes);
    }

    private int calculateAQI(double pm25) {
        if (pm25 <= 12.0) {
            // 0-12 -> 0-50
            return (int) Math.round(pm25 / 12.0 * 50);
        } else if (pm25 <= 35.4) {
            // 12.1-35.4 -> 51-100
            return (int) Math.round(50 + (pm25 - 12.0) / (35.4 - 12.0) * 50);
        } else if (pm25 <= 55.4) {
            // 35.5-55.4 -> 101-150
            return (int) Math.round(100 + (pm25 - 35.4) / (55.4 - 35.4) * 50);
        } else if (pm25 <= 150.4) {
            // 55.5-150.4 -> 151-200
            return (int) Math.round(150 + (pm25 - 55.4) / (150.4 - 55.4) * 50);
        } else {
            // >150.4 -> 201-300
            double val = 200 + (pm25 - 150.4) / 100.0 * 100;
            return (int) Math.min(Math.round(val), 300);
        }
    }

    public void collectAllCitiesData() {
        long startTime = System.currentTimeMillis();
        System.out.println("🔄 Начинаю фоновый сбор данных...");

        List<City> cities = cityRepository.findAll();
        int successCount = 0;
        int errorCount = 0;
        int notificationCount = 0;

        for (City city : cities) {
            try {
                String url = String.format(Locale.US,
                        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.6f&longitude=%.6f&hourly=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide",
                        city.getLatitude(), city.getLongitude()
                );

                String response = restTemplate.getForObject(url, String.class);
                JsonNode json = objectMapper.readTree(response);

                double pm25 = json.path("hourly").path("pm2_5").path(0).asDouble();
                double pm10 = json.path("hourly").path("pm10").path(0).asDouble();
                double co = json.path("hourly").path("carbon_monoxide").path(0).asDouble();
                double no2 = json.path("hourly").path("nitrogen_dioxide").path(0).asDouble();
                double so2 = json.path("hourly").path("sulphur_dioxide").path(0).asDouble();
                int aqi = calculateAQI(pm25);

                // Определяем текстовое качество воздуха
                String aqiText;
                if (aqi <= 50) aqiText = "Хорошо";
                else if (aqi <= 100) aqiText = "Умеренно";
                else if (aqi <= 150) aqiText = "Вредно для чувствительных групп";
                else if (aqi <= 200) aqiText = "Вредно";
                else aqiText = "Очень вредно";

                AirQualityHistory record = new AirQualityHistory();
                record.setCity(city);
                record.setLatitude(city.getLatitude());
                record.setLongitude(city.getLongitude());
                record.setPm25(pm25);
                record.setPm10(pm10);
                record.setCo(co);
                record.setNo2(no2);
                record.setSo2(so2);
                record.setAqi(aqi);
                record.setRequestedAt(LocalDateTime.now());

                historyRepository.save(record);
                successCount++;

                // ========== ОТПРАВКА УВЕДОМЛЕНИЙ ==========
                boolean isCritical = aqi >= 100;
                String status = isCritical ? "danger" : "safe";

                List<UserCitySubscription> subscriptions = subscriptionRepository.findByCity(city);

                for (UserCitySubscription sub : subscriptions) {
                    boolean needToSend = false;
                    String statusText;

                    if (isCritical && !sub.isLastStatusWasCritical()) {
                        needToSend = true;
                        statusText = "⚠️ Качество воздуха ухудшилось! Зафиксировано превышение ПДК.";
                        sub.setLastStatusWasCritical(true);
                    } else if (!isCritical && sub.isLastStatusWasCritical()) {
                        needToSend = true;
                        statusText = "✅ Качество воздуха нормализовалось!";
                        sub.setLastStatusWasCritical(false);
                    } else {
                        continue;
                    }

                    if (needToSend) {
                        subscriptionRepository.save(sub);

                        // Отправка в VK
                        if (sub.getUser().getMessengerId() != null && !sub.getUser().getMessengerId().isEmpty()) {
                            try {
                                Long vkPeerId = Long.parseLong(sub.getUser().getMessengerId());
                                vkBotService.sendAirQualityNotification(vkPeerId, city.getName(), aqi, isCritical);
                                System.out.println("📨 VK уведомление отправлено: " + city.getName());
                                Thread.sleep(100);
                            } catch (Exception e) {
                                System.out.println("📨 VK уведомление отправлено: " + city.getName());
                            }
                        }
                    }
                }
                // ========================================

                Thread.sleep(100);

            } catch (Exception e) {
                System.err.println("❌ Ошибка для города " + city.getName() + ": " + e.getMessage());
                errorCount++;
            }
        }

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("✅ Сбор завершен за " + duration + " сек. Успешно: " + successCount + ", Ошибок: " + errorCount);
        System.out.println("📨 Отправлено уведомлений: " + notificationCount);
    }

    private void updateNextExecutionTime() {
        String cron = getCronExpression();
        CronTrigger cronTrigger = new CronTrigger(cron);
        SimpleTriggerContext context = new SimpleTriggerContext();
        context.update(new Date(), new Date(), new Date());
        nextExecutionTime = cronTrigger.nextExecutionTime(context);
        if (nextExecutionTime != null) {
            System.out.println("⏰ Следующий сбор: " + nextExecutionTime);
        }
    }

    public Date getNextExecutionTime() {
        return nextExecutionTime;
    }

    public void scheduleTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }

        String cron = getCronExpression();
        System.out.println("📅 Планировщик настроен: cron = " + cron);

        CronTrigger cronTrigger = new CronTrigger(cron);

        scheduledTask = taskScheduler.schedule(
                this::collectAllCitiesData,
                context -> cronTrigger.nextExecutionTime(context).toInstant()
        );

        updateNextExecutionTime();
    }

    public void rescheduleTask() {
        scheduleTask();
    }
}