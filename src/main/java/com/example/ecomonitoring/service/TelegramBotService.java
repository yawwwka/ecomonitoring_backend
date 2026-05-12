package com.example.ecomonitoring.service;

import com.example.ecomonitoring.entity.User;
import com.example.ecomonitoring.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

@Component
public class TelegramBotService implements LongPollingSingleThreadUpdateConsumer {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Autowired
    private UserRepository userRepository;

    private TelegramClient telegramClient;

    @PostConstruct
    public void init() {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        System.out.println("🤖 Telegram бот запущен");
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            String userTelegramId = update.getMessage().getFrom().getId().toString();

            // Обработка команд
            if (messageText.equals("/start")) {
                sendMessage(chatId, """
                    🌍 *ЭкоМониторинг*
                    
                    Добро пожаловать! Вы подписаны на уведомления о качестве воздуха.
                    
                    Команды:
                    /subscribe - Подписаться на уведомления
                    /unsubscribe - Отписаться от уведомлений
                    /help - Помощь
                    """);

                // Сохраняем Telegram ID пользователя, если такой есть в системе
                linkTelegramId(userTelegramId, chatId);
            }
            else if (messageText.equals("/subscribe")) {
                linkTelegramId(userTelegramId, chatId);
                sendMessage(chatId, "✅ Вы подписаны на уведомления о превышении ПДК");
            }
            else if (messageText.equals("/unsubscribe")) {
                unlinkTelegramId(userTelegramId, chatId);
                sendMessage(chatId, "❌ Вы отписаны от уведомлений");
            }
            else if (messageText.equals("/help")) {
                sendMessage(chatId, """
                    📖 *Помощь*
                    
                    /start - Приветствие и подписка
                    /subscribe - Подписаться на уведомления
                    /unsubscribe - Отписаться от уведомлений
                    
                    *О сервисе:*
                    ЭкоМониторинг отслеживает качество воздуха и присылает уведомления при превышении ПДК.
                    """);
            }
        }
    }

    public void sendNotification(String telegramId, String cityName, int aqi, String quality) {
        if (telegramId == null || telegramId.isEmpty()) return;

        try {
            Long chatId = Long.parseLong(telegramId);
            String message = String.format("""
                🌍 *ЭкоМониторинг* - Оповещение
                
                🏙️ *Город:* %s
                📊 *AQI:* %d (%s)
                
                ⚠️ *Рекомендации:* %s
                """,
                    cityName, aqi, quality,
                    getAQIRecommendation(aqi));

            sendMessage(chatId, message);
        } catch (NumberFormatException e) {
            System.err.println("Неверный формат Telegram ID: " + telegramId);
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    private void linkTelegramId(String telegramId, Long chatId) {
        // Ищем пользователя по Telegram ID
        Optional<User> existingUser = userRepository.findByTelegramId(telegramId);

        if (existingUser.isPresent()) {
            System.out.println("Пользователь уже подписан: " + existingUser.get().getEmail());
        } else {
            sendMessage(chatId, "⚠️ Для получения уведомлений сначала зарегистрируйтесь на сайте ЭкоМониторинг и привяжите Telegram ID в настройках профиля.");
        }
    }

    private void unlinkTelegramId(String telegramId, Long chatId) {
        Optional<User> user = userRepository.findByTelegramId(telegramId);
        if (user.isPresent()) {
            user.get().setTelegramId(null);
            userRepository.save(user.get());
            sendMessage(chatId, "❌ Вы отписаны от уведомлений");
        }
    }

    private String getAQIRecommendation(int aqi) {
        if (aqi <= 50) return "Воздух чистый. Можно гулять без ограничений.";
        if (aqi <= 100) return "Воздух умеренно загрязнен. Людям с респираторными заболеваниями стоит ограничить прогулки.";
        if (aqi <= 150) return "Вредно для чувствительных групп. Ограничьте время на улице.";
        if (aqi <= 200) return "Вредно для всех. Закройте окна, используйте маску на улице.";
        return "Очень вредно. Избегайте длительного пребывания на улице, используйте очистители воздуха.";
    }
}