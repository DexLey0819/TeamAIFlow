package com.example.teamflow.service.ai;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.WeekFields;

public final class AiTextSupport {
    private AiTextSupport() {
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "未提供";
    }

    public static String nullSafe(String value) {
        return StringUtils.hasText(value) ? value : "未提供";
    }

    public static String valueOf(Object value) {
        return value == null ? "未提供" : value.toString();
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    public static String currentPeriod() {
        LocalDate date = LocalDate.now();
        WeekFields weekFields = WeekFields.ISO;
        return String.format("%04d-W%02d", date.get(weekFields.weekBasedYear()), date.get(weekFields.weekOfWeekBasedYear()));
    }

    public static String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "未提供";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    public static int toInt(long value) {
        return Math.toIntExact(Math.min(value, Integer.MAX_VALUE));
    }
}
