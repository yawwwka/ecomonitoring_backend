package com.example.ecomonitoring.service;

import com.example.ecomonitoring.entity.SystemSetting;
import com.example.ecomonitoring.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SettingService {

    @Autowired
    private SystemSettingRepository settingRepository;

    @Autowired
    private ScheduledDataCollectionService scheduler;

    private static final long DEFAULT_COLLECTION_INTERVAL = 60;

    public long getCollectionIntervalMinutes() {
        try {
            return settingRepository.findBySettingKey("collection_interval_minutes")
                    .map(s -> Long.parseLong(s.getSettingValue()))
                    .orElse(DEFAULT_COLLECTION_INTERVAL);
        } catch (Exception e) {
            return DEFAULT_COLLECTION_INTERVAL;
        }
    }

    public void setCollectionIntervalMinutes(long minutes) {
        if (minutes < 1) minutes = 1;
        if (minutes > 1440) minutes = 1440;

        SystemSetting setting = settingRepository.findBySettingKey("collection_interval_minutes")
                .orElse(new SystemSetting("collection_interval_minutes", String.valueOf(minutes)));
        setting.setSettingValue(String.valueOf(minutes));
        settingRepository.save(setting);

        System.out.println("⚙️ Интервал изменен на " + minutes + " минут, перепланирую задачу...");
        scheduler.rescheduleTask();
    }
}