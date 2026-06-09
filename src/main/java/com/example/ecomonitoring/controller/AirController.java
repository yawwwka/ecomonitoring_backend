package com.example.ecomonitoring.controller;

import com.example.ecomonitoring.entity.AirQualityHistory;
import com.example.ecomonitoring.entity.City;
import com.example.ecomonitoring.repository.AirQualityHistoryRepository;
import com.example.ecomonitoring.repository.CityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.Font;
import com.itextpdf.text.pdf.draw.LineSeparator;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import org.springframework.http.HttpHeaders;
import com.itextpdf.text.BaseColor;
import java.time.LocalDateTime;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/air")
public class AirController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private AirQualityHistoryRepository historyRepository;

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

    @GetMapping
    public String getAirQuality(@RequestParam double lat, @RequestParam double lon) {
        String url = String.format(Locale.US,
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.6f&longitude=%.6f&hourly=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide",
                lat, lon
        );
        String response = restTemplate.getForObject(url, String.class);

        // ✅ Сохранения НЕТ — только возвращаем данные
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
    public ResponseEntity<List<AirQualityHistory>> getCityHistory(@PathVariable Long cityId) {
        List<AirQualityHistory> history = historyRepository.findByCityIdOrderByRequestedAtDesc(cityId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/cities/search")
    public ResponseEntity<List<City>> searchCities(@RequestParam String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(new ArrayList<>());
        }
        List<City> cities = cityRepository.searchByName(query.trim());
        return ResponseEntity.ok(cities);
    }

    @GetMapping("/history/export/{cityId}")
    public ResponseEntity<byte[]> exportHistoryToCsv(
            @PathVariable Long cityId,
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<AirQualityHistory> history = historyRepository.findByCityIdAndRequestedAtAfter(cityId, since);

        StringBuilder csv = new StringBuilder();
        // Используем точку с запятой и кавычки для защиты от Excel
        csv.append("ID;Дата;AQI;PM2.5;PM10;CO;NO2;SO2\n");

        for (AirQualityHistory record : history) {
            // Форматируем дату в понятный вид
            String formattedDate = record.getRequestedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            csv.append(record.getId()).append(";")
                    .append(formattedDate).append(";")
                    .append(record.getAqi()).append(";")
                    .append(record.getPm25() != null ? record.getPm25().toString().replace(".", ",") : "").append(";")
                    .append(record.getPm10() != null ? record.getPm10().toString().replace(".", ",") : "").append(";")
                    .append(record.getCo() != null ? record.getCo().toString().replace(".", ",") : "").append(";")
                    .append(record.getNo2() != null ? record.getNo2().toString().replace(".", ",") : "").append(";")
                    .append(record.getSo2() != null ? record.getSo2().toString().replace(".", ",") : "").append("\n");
        }

        // UTF-8 с BOM для корректного отображения в Excel
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);

        // Добавляем BOM (Byte Order Mark) для UTF-8
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] result = new byte[bom.length + bytes.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(bytes, 0, result, bom.length, bytes.length);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=air_quality_city_" + cityId + "_" + days + "days.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result);
    }

    @GetMapping("/history/export/pdf/{cityId}")
    public ResponseEntity<byte[]> exportPdfReport(
            @PathVariable Long cityId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "aqi") String metric) {

        try {
            City city = cityRepository.findById(cityId).orElseThrow();
            LocalDateTime since = LocalDateTime.now().minusDays(days);
            List<AirQualityHistory> history = historyRepository.findByCityIdAndRequestedAtAfter(cityId, since);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // Русский шрифт (используем стандартный, можно заменить на настоящий)
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 24, Font.BOLD);
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD);
            Font normalFont = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL);
            Font smallFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);

            // ========== ТИТУЛЬНЫЙ ЛИСТ ==========
            Paragraph title = new Paragraph("ЭкоМониторинг", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            document.add(new Paragraph(" ", normalFont));

            Paragraph reportTitle = new Paragraph("ОТЧЕТ О КАЧЕСТВЕ ВОЗДУХА", headerFont);
            reportTitle.setAlignment(Element.ALIGN_CENTER);
            document.add(reportTitle);

            document.add(new Paragraph(" ", normalFont));

            // Информация об отчете
            document.add(new Paragraph("Город: " + city.getName(), normalFont));
            document.add(new Paragraph("Регион: " + (city.getRegion() != null ? city.getRegion() : "-"), normalFont));
            document.add(new Paragraph("Период: последние " + days + " дней", normalFont));
            document.add(new Paragraph("Дата формирования: " + new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new java.util.Date()), normalFont));
            document.add(new Paragraph("Количество измерений: " + history.size(), normalFont));

            document.add(new Paragraph(" ", normalFont));

            // Статистика
            if (!history.isEmpty()) {
                double avgAqi = history.stream().mapToInt(AirQualityHistory::getAqi).average().orElse(0);
                int maxAqi = history.stream().mapToInt(AirQualityHistory::getAqi).max().orElse(0);
                int minAqi = history.stream().mapToInt(AirQualityHistory::getAqi).min().orElse(0);

                PdfPTable statsTable = new PdfPTable(2);
                statsTable.setWidthPercentage(100);
                statsTable.addCell(new PdfPCell(new Phrase("Средний AQI", normalFont)));
                statsTable.addCell(new PdfPCell(new Phrase(String.format("%.1f", avgAqi), normalFont)));
                statsTable.addCell(new PdfPCell(new Phrase("Максимальный AQI", normalFont)));
                statsTable.addCell(new PdfPCell(new Phrase(String.valueOf(maxAqi), normalFont)));
                statsTable.addCell(new PdfPCell(new Phrase("Минимальный AQI", normalFont)));
                statsTable.addCell(new PdfPCell(new Phrase(String.valueOf(minAqi), normalFont)));
                document.add(statsTable);
            }

            document.newPage();

            // ========== ГРАФИКИ ==========
            // Для графиков нужно сгенерировать изображения на бэкенде или передать данные и рисовать на клиенте
            // Упрощенно: выводим таблицу с данными вместо графиков

            // ========== ТАБЛИЦА С ДАННЫМИ ==========
            Paragraph tableHeader = new Paragraph("Детальные данные", headerFont);
            tableHeader.setAlignment(Element.ALIGN_CENTER);
            document.add(tableHeader);
            document.add(new Paragraph(" ", normalFont));

            PdfPTable dataTable = new PdfPTable(7);
            dataTable.setWidthPercentage(100);
            dataTable.setWidths(new float[]{2, 1, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f});

            // Заголовки таблицы
            String[] headers = {"Дата", "AQI", "PM2.5", "PM10", "CO", "NO₂", "SO₂"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new BaseColor(240, 240, 240));
                dataTable.addCell(cell);
            }

            // Данные (последние 50 записей, чтобы не раздувать PDF)
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
            history.stream().limit(50).forEach(record -> {
                dataTable.addCell(dateFormat.format(java.util.Date.from(record.getRequestedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())));
                dataTable.addCell(String.valueOf(record.getAqi()));
                dataTable.addCell(record.getPm25() != null ? String.format("%.1f", record.getPm25()) : "-");
                dataTable.addCell(record.getPm10() != null ? String.format("%.1f", record.getPm10()) : "-");
                dataTable.addCell(record.getCo() != null ? String.format("%.1f", record.getCo()) : "-");
                dataTable.addCell(record.getNo2() != null ? String.format("%.1f", record.getNo2()) : "-");
                dataTable.addCell(record.getSo2() != null ? String.format("%.1f", record.getSo2()) : "-");
            });

            document.add(dataTable);

            // ========== ПОДПИСЬ И ИСТОЧНИК ==========
            document.newPage();
            Paragraph conclusion = new Paragraph("ЗАКЛЮЧЕНИЕ", headerFont);
            conclusion.setAlignment(Element.ALIGN_CENTER);
            document.add(conclusion);
            document.add(new Paragraph(" ", normalFont));

            String conclusionText = "На основании проведенного анализа данных о качестве воздуха в городе " + city.getName() +
                    " за последние " + days + " дней " + (history.stream().anyMatch(h -> h.getAqi() > 100) ?
                    "были зафиксированы превышения нормативных значений. Рекомендуется ограничить пребывание на открытом воздухе в периоды высокого загрязнения." :
                    "превышений нормативных значений не зафиксировано. Качество воздуха оценивается как удовлетворительное.");

            document.add(new Paragraph(conclusionText, normalFont));
            document.add(new Paragraph(" ", normalFont));
            document.add(new Paragraph("Источник данных: Open-Meteo API", normalFont));
            document.add(new Paragraph("Система мониторинга: ЭкоМониторинг", normalFont));

            document.close();

            byte[] pdfBytes = out.toByteArray();

            HttpHeaders headersResp = new HttpHeaders();
            headersResp.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
            headersResp.setContentDispositionFormData("attachment", "report_" + city.getName() + "_" + days + "days.pdf");

            return new ResponseEntity<>(pdfBytes, headersResp, org.springframework.http.HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/history/{cityId}/period")
    public ResponseEntity<List<AirQualityHistory>> getHistoryByPeriod(
            @PathVariable Long cityId,
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<AirQualityHistory> history = historyRepository.findByCityIdAndRequestedAtAfter(cityId, since);
        return ResponseEntity.ok(history);
    }


}