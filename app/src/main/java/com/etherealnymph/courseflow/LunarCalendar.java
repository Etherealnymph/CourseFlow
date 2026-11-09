package com.etherealnymph.courseflow;

import android.icu.util.ChineseCalendar;

import java.time.LocalDate;
import java.time.ZoneId;

/** Lunar day labels via Android's ICU Chinese calendar. */
public final class LunarCalendar {

    private static final String[] DAYS = {
            "", "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };

    private LunarCalendar() {}

    public static String dayLabel(LocalDate date) {
        try {
            ChineseCalendar cc = new ChineseCalendar();
            cc.setTimeInMillis(date.atTime(12, 0).atZone(ZoneId.systemDefault())
                    .toInstant().toEpochMilli());
            int day = cc.get(ChineseCalendar.DAY_OF_MONTH);
            if (day < 1 || day >= DAYS.length) return "";
            return DAYS[day];
        } catch (Throwable e) {
            return "";
        }
    }

    public static boolean isLunarFirstDay(LocalDate date) {
        try {
            ChineseCalendar cc = new ChineseCalendar();
            cc.setTimeInMillis(date.atTime(12, 0).atZone(ZoneId.systemDefault())
                    .toInstant().toEpochMilli());
            return cc.get(ChineseCalendar.DAY_OF_MONTH) == 1;
        } catch (Throwable e) {
            return false;
        }
    }

    public static String monthLabel(LocalDate date) {
        try {
            ChineseCalendar cc = new ChineseCalendar();
            cc.setTimeInMillis(date.atTime(12, 0).atZone(ZoneId.systemDefault())
                    .toInstant().toEpochMilli());
            int month = cc.get(ChineseCalendar.MONTH) + 1;
            boolean leap = cc.get(ChineseCalendar.IS_LEAP_MONTH) == 1;
            String[] names = {"", "正月", "二月", "三月", "四月", "五月", "六月",
                    "七月", "八月", "九月", "十月", "冬月", "腊月"};
            String n = month >= 1 && month < names.length ? names[month] : month + "月";
            return (leap ? "闰" : "") + n;
        } catch (Throwable e) {
            return "";
        }
    }
}
