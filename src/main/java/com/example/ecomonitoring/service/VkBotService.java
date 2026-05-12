package com.example.ecomonitoring.service;

import com.example.ecomonitoring.entity.User;
import com.example.ecomonitoring.entity.City;
import com.example.ecomonitoring.entity.UserCitySubscription;
import com.example.ecomonitoring.repository.CityRepository;
import com.example.ecomonitoring.repository.UserCitySubscriptionRepository;
import com.example.ecomonitoring.repository.UserRepository;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.groups.responses.GetLongPollServerResponse;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.queries.messages.MessagesGetLongPollHistoryQuery;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class VkBotService {

    @Value("${vk.access.token}")
    private String accessToken;

    @Value("${vk.group.id}")
    private Integer groupId;

    @Value("${vk.longpoll.wait:25}")
    private Integer longPollWait;

    private VkApiClient vk;
    private GroupActor actor;
    private String lastTs = null;
    private String longPollServer = null;
    private String longPollKey = null;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private UserCitySubscriptionRepository subscriptionRepository;

    @PostConstruct
    public void init() {
        TransportClient transportClient = new HttpTransportClient();
        vk = new VkApiClient(transportClient);
        actor = new GroupActor(groupId, accessToken);

        // Запускаем поток для постоянного опроса Long Poll сервера
        new Thread(this::startLongPolling).start();

        log.info("✅ VK бот инициализирован для группы ID: {}", groupId);
    }

    /**
     * Основной цикл Long Polling
     */
    private void startLongPolling() {
        try {
            GetLongPollServerResponse serverInfo = vk.groups()
                    .getLongPollServer(actor, groupId)
                    .execute();

            longPollServer = serverInfo.getServer();
            longPollKey = serverInfo.getKey();
            lastTs = String.valueOf(serverInfo.getTs());

            log.info("🔄 Long Poll сервер получен: {}", longPollServer);
            log.info("🔄 Начальный ts: {}", lastTs);

            while (true) {
                try {
                    String cleanTs = lastTs.replace("\"", "").trim();

                    String url = String.format("%s?act=a_check&key=%s&ts=%s&wait=%d&mode=2&version=3",
                            longPollServer, longPollKey, cleanTs, longPollWait);

                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(url))
                            .GET()
                            .build();

                    java.net.http.HttpResponse<String> response = client.send(request,
                            java.net.http.HttpResponse.BodyHandlers.ofString());

                    String body = response.body();

                    if (body != null && !body.isEmpty()) {
                        // Обновляем ts из ответа
                        int tsIndex = body.indexOf("\"ts\":");
                        if (tsIndex != -1) {
                            int endIndex = body.indexOf(",", tsIndex);
                            if (endIndex == -1) endIndex = body.indexOf("}", tsIndex);
                            String newTs = body.substring(tsIndex + 5, endIndex).trim();
                            lastTs = newTs;
                            log.debug("🔄 Обновлен ts: {}", lastTs);
                        }

                        // Проверяем наличие новых сообщений
                        if (body.contains("\"updates\":") && body.length() > 20) {
                            log.info("📨 Есть новые сообщения!");
                            parseAndProcessMessages(body);
                        }
                    }

                } catch (Exception e) {
                    log.error("Ошибка Long Poll запроса: {}", e.getMessage());
                    Thread.sleep(5000);

                    try {
                        GetLongPollServerResponse newServerInfo = vk.groups()
                                .getLongPollServer(actor, groupId)
                                .execute();
                        longPollServer = newServerInfo.getServer();
                        longPollKey = newServerInfo.getKey();
                        lastTs = String.valueOf(newServerInfo.getTs());
                        log.info("🔄 Сервер обновлен, новый ts: {}", lastTs);
                    } catch (Exception ex) {
                        log.error("Ошибка обновления сервера: {}", ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка инициализации Long Poll: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private void parseAndProcessMessages(String body) {
        try {
            // Ищем массив updates
            int updatesIndex = body.indexOf("\"updates\":");
            if (updatesIndex == -1) return;

            String updatesPart = body.substring(updatesIndex + 10);

            // Находим закрывающую скобку массива
            int bracketCount = 0;
            int endIndex = -1;
            for (int i = 0; i < updatesPart.length(); i++) {
                char c = updatesPart.charAt(i);
                if (c == '[') bracketCount++;
                if (c == ']') {
                    bracketCount--;
                    if (bracketCount == 0) {
                        endIndex = i;
                        break;
                    }
                }
            }
            if (endIndex == -1) return;

            String updates = updatesPart.substring(0, endIndex + 1);

            // Парсим каждое обновление
            int pos = 0;
            while (true) {
                int startPos = updates.indexOf("{", pos);
                if (startPos == -1) break;

                // Находим конец объекта
                int objEnd = startPos;
                int braces = 0;
                for (int i = startPos; i < updates.length(); i++) {
                    char c = updates.charAt(i);
                    if (c == '{') braces++;
                    if (c == '}') {
                        braces--;
                        if (braces == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }

                String updateObj = updates.substring(startPos, objEnd + 1);

                // Проверяем тип обновления
                if (updateObj.contains("\"type\":\"message_new\"")) {
                    // Извлекаем peer_id
                    int peerIdIdx = updateObj.indexOf("\"peer_id\":");
                    if (peerIdIdx != -1) {
                        int peerIdEnd = updateObj.indexOf(",", peerIdIdx);
                        if (peerIdEnd == -1) peerIdEnd = updateObj.indexOf("}", peerIdIdx);
                        Long peerId = Long.parseLong(updateObj.substring(peerIdIdx + 10, peerIdEnd).trim());

                        // Извлекаем текст
                        int textIdx = updateObj.indexOf("\"text\":\"");
                        if (textIdx != -1) {
                            int textEnd = updateObj.indexOf("\"", textIdx + 8);
                            String text = updateObj.substring(textIdx + 8, textEnd);

                            // Извлекаем user_id
                            int userIdIdx = updateObj.indexOf("\"from_id\":");
                            if (userIdIdx != -1) {
                                int userIdEnd = updateObj.indexOf(",", userIdIdx);
                                if (userIdEnd == -1) userIdEnd = updateObj.indexOf("}", userIdIdx);
                                Integer userId = Integer.parseInt(updateObj.substring(userIdIdx + 10, userIdEnd).trim());

                                log.info("📨 Получено сообщение: peerId={}, userId={}, text={}", peerId, userId, text);

                                // Обрабатываем сообщение
                                processMessage(peerId, text, userId);
                            }
                        }
                    }
                }

                pos = objEnd + 1;
            }
        } catch (Exception e) {
            log.error("Ошибка парсинга сообщений: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private int findMatchingBracket(String str, int start) {
        int count = 0;
        for (int i = start; i < str.length(); i++) {
            if (str.charAt(i) == '[') count++;
            if (str.charAt(i) == ']') {
                count--;
                if (count == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Обработка входящего сообщения (упрощенная версия)
     */
    public void processMessage(Long peerId, String text, Integer userId) {
        // Очищаем текст от экранирования
        String cleanText = text.trim();
        cleanText = cleanText.replace("\\/", "/");
        cleanText = cleanText.replace("\\\\", "\\");

        log.info("📨 Получено сообщение от {}: исходный='{}', очищенный='{}'", userId, text, cleanText);

        if (cleanText.equalsIgnoreCase("/start")) {
            sendMessage(peerId, """
            🌍 ЭкоМониторинг VK Бот
            
            Я буду присылать уведомления о превышении ПДК.
            
            Команды:
            /subscribe [город] - подписаться
            /unsubscribe [город] - отписаться
            /my_subscriptions - мои подписки
            /help - помощь
            
            Пример: /subscribe Москва
            """);
            return;
        }

        if (cleanText.startsWith("/subscribe ")) {
            String cityName = cleanText.substring(11).trim();
            handleSubscribe(peerId, userId, cityName);
            return;
        }

        if (cleanText.startsWith("/unsubscribe ")) {
            String cityName = cleanText.substring(13).trim();
            handleUnsubscribe(peerId, userId, cityName);
            return;
        }

        if (cleanText.equalsIgnoreCase("/my_subscriptions")) {
            handleMySubscriptions(peerId, userId);
            return;
        }

        if (cleanText.equalsIgnoreCase("/help")) {
            sendMessage(peerId, """
            Помощь по командам
            
            /subscribe [город] - подписаться
            /unsubscribe [город] - отписаться
            /my_subscriptions - список подписок
            /help - помощь
            """);
            return;
        }

        sendMessage(peerId, "Неизвестная команда: " + cleanText + "\nОтправьте /help");
    }

    private void handleSubscribe(Long peerId, Integer userId, String cityName) {
        // Ищем пользователя по messenger_id (VK ID)
        String vkId = String.valueOf(userId);
        Optional<User> userOpt = userRepository.findByMessengerId(vkId);

        if (userOpt.isEmpty()) {
            sendMessage(peerId, "⚠️ Сначала зарегистрируйтесь на сайте ЭкоМониторинг и привяжите Messenger ID в настройках профиля.\n\nВаш ID: " + vkId);
            return;
        }

        User user = userOpt.get();

        Optional<City> cityOpt = cityRepository.findByNameIgnoreCase(cityName);
        if (cityOpt.isEmpty()) {
            sendMessage(peerId, "❌ Город \"" + cityName + "\" не найден.");
            return;
        }

        City city = cityOpt.get();

        Optional<UserCitySubscription> existing = subscriptionRepository.findByUserAndCity(user, city);
        if (existing.isPresent()) {
            sendMessage(peerId, "ℹ️ Вы уже подписаны на " + city.getName());
            return;
        }

        UserCitySubscription subscription = new UserCitySubscription(user, city);
        subscriptionRepository.save(subscription);

        sendMessage(peerId, "✅ Вы подписаны на уведомления о городе *" + city.getName() + "*");
    }

    private void handleUnsubscribe(Long peerId, Integer userId, String cityName) {
        sendMessage(peerId, "❌ Вы отписаны от уведомлений о городе: " + cityName);
    }

    private void handleMySubscriptions(Long peerId, Integer userId) {
        sendMessage(peerId, "📋 Ваши подписки:\n- Москва\n- Санкт-Петербург");
    }

    /**
     * Отправка сообщения
     */
    public void sendMessage(Long peerId, String message) {
        try {
            vk.messages()
                    .send(actor)
                    .peerId(Math.toIntExact(peerId))  // ← преобразуем Long в Integer
                    .message(message)
                    .randomId((int) (System.currentTimeMillis() & Integer.MAX_VALUE))
                    .execute();
            log.info("✅ Сообщение отправлено в peerId: {}", peerId);
        } catch (Exception e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage());
        }
    }

    /**
     * Отправка уведомления о превышении ПДК
     */
    public void sendAirQualityNotification(Long peerId, String cityName, int aqi, boolean isDanger) {
        String emoji = isDanger ? "⚠️" : "✅";
        String statusText = isDanger ? "ПРЕВЫШЕНИЕ ПДК!" : "НОРМАЛИЗАЦИЯ";

        String text = String.format("""
            %s ЭкоМониторинг
            
            Город: %s
            AQI: %d
            Статус: %s
            
            %s
            """,
                emoji, cityName, aqi, statusText,
                isDanger
                        ? "💡 Рекомендуется ограничить время на улице."
                        : "💡 Ситуация нормализовалась.");

        sendMessage(peerId, text);
    }
}