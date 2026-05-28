package com.example.ecomonitoring.service;

import com.example.ecomonitoring.entity.City;
import com.example.ecomonitoring.entity.User;
import com.example.ecomonitoring.entity.UserCitySubscription;
import com.example.ecomonitoring.repository.CityRepository;
import com.example.ecomonitoring.repository.UserCitySubscriptionRepository;
import com.example.ecomonitoring.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SubscriptionService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private UserCitySubscriptionRepository subscriptionRepository;

    @Transactional
    public String subscribe(Integer userId, String cityName) {
        String vkId = String.valueOf(userId);
        Optional<User> userOpt = userRepository.findByMessengerId(vkId);

        if (userOpt.isEmpty()) {
            return "⚠️ Сначала зарегистрируйтесь на сайте и привяжите Messenger ID.\n\nВаш ID: " + vkId;
        }

        User user = userOpt.get();
        Optional<City> cityOpt = cityRepository.findByNameIgnoreCase(cityName);

        if (cityOpt.isEmpty()) {
            return "❌ Город \"" + cityName + "\" не найден.";
        }

        City city = cityOpt.get();
        Optional<UserCitySubscription> existing = subscriptionRepository.findByUserAndCity(user, city);

        if (existing.isPresent()) {
            return "ℹ️ Вы уже подписаны на " + city.getName();
        }

        UserCitySubscription subscription = new UserCitySubscription(user, city);
        subscriptionRepository.save(subscription);
        return "✅ Вы подписаны на уведомления о городе *" + city.getName() + "*";
    }

    @Transactional
    public String unsubscribe(Integer userId, String cityName) {
        String vkId = String.valueOf(userId);
        Optional<User> userOpt = userRepository.findByMessengerId(vkId);

        if (userOpt.isEmpty()) {
            return "⚠️ Пользователь не найден";
        }

        User user = userOpt.get();
        Optional<City> cityOpt = cityRepository.findByNameIgnoreCase(cityName);

        if (cityOpt.isEmpty()) {
            return "❌ Город \"" + cityName + "\" не найден";
        }

        City city = cityOpt.get();
        subscriptionRepository.deleteByUserAndCity(user, city);
        return "❌ Вы отписаны от уведомлений о городе *" + city.getName() + "*";
    }

    @Transactional(readOnly = true)
    public String getMySubscriptions(Integer userId) {
        String vkId = String.valueOf(userId);
        Optional<User> userOpt = userRepository.findByMessengerId(vkId);

        if (userOpt.isEmpty()) {
            return "⚠️ Пользователь не найден. Сначала зарегистрируйтесь на сайте.";
        }

        User user = userOpt.get();
        List<UserCitySubscription> subscriptions = subscriptionRepository.findByUser(user);

        if (subscriptions.isEmpty()) {
            return "📭 У вас нет активных подписок.\n\nИспользуйте команду:\n/subscribe [город]";
        }

        StringBuilder sb = new StringBuilder("📋 *Ваши подписки:*\n\n");
        for (UserCitySubscription sub : subscriptions) {
            sb.append("🏙️ *").append(sub.getCity().getName()).append("*\n");
        }
        return sb.toString();
    }
}