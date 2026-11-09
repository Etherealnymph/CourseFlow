package com.etherealnymph.courseflow;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class Dates {

    public static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEK_SHORT = {"", "一", "二", "三", "四", "五", "六", "日"};
    private static final String[] WEEK_FULL = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private Dates() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDate mondayOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    public static int weekOf(LocalDate termStart, LocalDate date) {
        LocalDate start = mondayOf(termStart);
        long days = ChronoUnit.DAYS.between(start, date);
        return (int) Math.floorDiv(days, 7L) + 1;
    }

    public static LocalDate weekStart(LocalDate termStart, int week) {
        return mondayOf(termStart).plusWeeks(Math.max(0, week - 1L));
    }

    public static int dow(LocalDate date) {
        return date.getDayOfWeek().getValue();
    }

    public static String weekShort(int dow) {
        return WEEK_SHORT[Math.max(1, Math.min(7, dow))];
    }

    public static String weekFull(int dow) {
        return WEEK_FULL[Math.max(1, Math.min(7, dow))];
    }

    public static String time(LocalTime time) {
        return time.format(HM);
    }

    public static String time(int minutes) {
        int m = Math.max(0, Math.min(23 * 60 + 59, minutes));
        return String.format(Locale.CHINA, "%02d:%02d", m / 60, m % 60);
    }

    public static int minutes(String hm) {
        if (hm == null) return -1;
        String s = hm.trim();
        if (s.isEmpty()) return -1;
        try {
            LocalTime t = LocalTime.parse(s.length() == 5 ? s : padTime(s), HM);
            return t.getHour() * 60 + t.getMinute();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }

    private static String padTime(String s) {
        int i = s.indexOf(':');
        if (i == 1) return "0" + s;
        if (i == 2) return s;
        return s;
    }

    public static LocalDate parseDate(String s) {
        if (s == null) return null;
        try {
            return LocalDate.parse(s.trim(), DATE);
        } catch (Exception e) {
            return null;
        }
    }

    public static String date(LocalDate date) {
        return date.format(DATE);
    }

    public static String monthDay(LocalDate date) {
        return (date.getMonthValue()) + "月" + date.getDayOfMonth() + "日";
    }

    public static String monthDayDot(LocalDate date) {
        return date.getMonthValue() + "." + date.getDayOfMonth();
    }

    public static String longDate(LocalDate date) {
        return monthDay(date) + " " + weekFull(dow(date));
    }

    public static String weekRange(LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        if (monday.getMonthValue() == sunday.getMonthValue()) {
            return monthDay(monday) + " – " + sunday.getDayOfMonth() + "日";
        }
        return monthDay(monday) + " – " + monthDay(sunday);
    }

    public static String relative(LocalDate date) {
        LocalDate now = today();
        long d = ChronoUnit.DAYS.between(now, date);
        if (d == 0) return "今天";
        if (d == 1) return "明天";
        if (d == -1) return "昨天";
        if (d == 2) return "后天";
        return monthDay(date);
    }

    public static long at(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ZONE).toInstant().toEpochMilli();
    }

    public static LocalDate dateOf(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate();
    }

    public static LocalTime timeOf(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalTime();
    }

    public static String dueLabel(long millis) {
        LocalDate d = dateOf(millis);
        return relative(d) + " " + time(timeOf(millis));
    }

    public static String dueFull(long millis) {
        LocalDate d = dateOf(millis);
        return d.getYear() + "年" + monthDay(d) + " " + weekFull(dow(d)) + " " + time(timeOf(millis));
    }

    public static String nowTime() {
        return time(LocalTime.now(ZONE));
    }

    public static String stamp() {
        return LocalDate.now(ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
