package com.etherealnymph.courseflow;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Approximate but stable 24-solar-term dates for the Gregorian calendar. */
public final class SolarTerms {
    private static final String[] NAMES = {
            "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨",
            "立夏", "小满", "芒种", "夏至", "小暑", "大暑", "立秋", "处暑",
            "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    };

    private static final int[] MINUTES = {
            0, 21208, 42467, 63836, 85337, 107014, 128867, 150921,
            173149, 195551, 218072, 240693, 263343, 285989, 308563, 331033,
            353350, 375494, 397447, 419210, 440795, 462224, 483532, 504758
    };

    private static final Map<Integer, List<LocalDate>> CACHE = new HashMap<>();

    private SolarTerms() {
    }

    public static String nameOf(LocalDate date) {
        List<LocalDate> dates = year(date.getYear());
        for (int i = 0; i < dates.size(); i++) {
            if (dates.get(i).equals(date)) return NAMES[i];
        }
        return "";
    }

    private static synchronized List<LocalDate> year(int year) {
        List<LocalDate> existing = CACHE.get(year);
        if (existing != null) return existing;
        List<LocalDate> list = new ArrayList<>(24);
        long base = ZonedDateTime.of(1900, 1, 6, 2, 5, 0, 0,
                ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        for (int i = 0; i < 24; i++) {
            double millis = 31556925974.7 * (year - 1900) + MINUTES[i] * 60000.0;
            long at = base + Math.round(millis);
            list.add(Instant.ofEpochMilli(at).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate());
        }
        CACHE.put(year, list);
        return list;
    }
}
