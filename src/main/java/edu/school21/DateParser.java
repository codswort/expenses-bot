package edu.school21;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicInteger;

public class DateParser {
    public LocalDateTime parseDate(String dateStr, AtomicInteger message) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        try {
            // Парсим только дату (без учета времени)
            LocalDate parsedDate = LocalDate.parse(dateStr, formatter);

            // Преобразуем LocalDate в LocalDateTime с временем на полночь
            LocalDateTime parsedDateTime = parsedDate.atStartOfDay();

            // Проверяем, не изменилась ли дата после форматирования (для отлова несуществующих дат)
            String reformattedDate = parsedDate.format(formatter);
            if (!reformattedDate.equals(dateStr)) {
                message.set(1);
                return null; // Некорректная дата (например, 30.02.2024)
            }

            // Проверяем, что дата в прошлом
            if (parsedDateTime.isAfter(LocalDateTime.now())) {
                message.set(2);
                return null; // Дата в будущем
            }

            message.set(0);
            return parsedDateTime; // Возвращаем LocalDateTime с временем на полночь
        } catch (DateTimeParseException e) {
            message.set(1);
            return null; // Ошибка парсинга
        }
    }
}
