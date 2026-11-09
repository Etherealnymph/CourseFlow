package com.etherealnymph.courseflow;

import android.icu.util.ChineseCalendar;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * A small built-in holiday calendar for the common Chinese festivals. It is
 * deliberately conservative: official make-up workdays still come from the
 * user's holiday JSON / manual entries.
 */
public final class DefaultHolidays {
    private static final Map<LocalDate, String> CACHE = new HashMap<>();

    private DefaultHolidays() {
    }

    public static String nameOf(LocalDate date) {
        String cached = CACHE.get(date);
        if (cached != null) return cached;
        String name = compute(date);
        CACHE.put(date, name);
        return name;
    }

    public static boolean isHoliday(LocalDate date) {
        return !nameOf(date).isEmpty();
    }

    private static String compute(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        if (month == 1 && day == 1) return "元旦";
        if (month == 5 && day >= 1 && day <= 5) return "劳动节";
        if (month == 10 && day >= 1 && day <= 7) return "国庆节";
        String term = SolarTerms.nameOf(date);
        if ("清明".equals(term)) return "清明节";

        try {
            ChineseCalendar cc = new ChineseCalendar();
            cc.setTimeInMillis(date.atTime(12, 0)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            int lunarMonth = cc.get(ChineseCalendar.MONTH) + 1;
            int lunarDay = cc.get(ChineseCalendar.DAY_OF_MONTH);
            boolean leap = cc.get(ChineseCalendar.IS_LEAP_MONTH) == 1;
            if (!leap && lunarMonth == 1 && lunarDay >= 1 && lunarDay <= 3) return "春节";
            if (!leap && lunarMonth == 5 && lunarDay == 5) return "端午节";
            if (!leap && lunarMonth == 8 && lunarDay == 15) return "中秋节";
        } catch (Throwable ignored) {
        }
        return "";
    }
}
