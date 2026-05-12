package com.example.ecomonitoring.repository;

import com.example.ecomonitoring.entity.NotificationSetting;
import com.example.ecomonitoring.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    Optional<NotificationSetting> findByUser(User user);

    boolean existsByUser(User user);
}