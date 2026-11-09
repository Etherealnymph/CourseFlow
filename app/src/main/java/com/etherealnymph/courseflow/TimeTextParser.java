package com.etherealnymph.courseflow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Best-effort Chinese date/time recognition for todo titles and bodies. */
public final class TimeTextParser {

    private static final Pattern FULL = Pattern.compile(
            "(20\\d{2})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*日?\\s*"
                    + "(?:(上午|下午|晚上|中午)?\\s*(\\d{1,2})\\s*[:：点时]\\s*(\\d{1,2})?\\s*分?)?");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?");
    private static final Pattern TIME = Pattern.compile(
            "(上午|下午|晚上|中午)?\\s*(\\d{1,2})\\s*[:：点时]\\s*(\\d{1,2})?\\s*分?");
    private static final Pattern RELATIVE = Pattern.compile("(今天|今日|明天|明日|后天|大后天)");
    private static final Pattern MONTH_DAY_DOT = Pattern.compile("(?<!\\d)(\\d{1,2})\\.(\\d{1,2})(?!\\d)");

    private TimeTextParser() {}

    public static long parse(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        String s = text.trim();
        LocalDate today = Dates.today();

        Matcher full = FULL.matcher(s);
        if (full.find()) {
            int year = Integer.parseInt(full.group(1));
            int month = Integer.parseInt(full.group(2));
            int day = Integer.parseInt(full.group(3));
            LocalDate date;
            try {
                date = LocalDate.of(year, month, day);
            } catch (Exception e) {
                return 0;
            }
            int hour = 0;
            int minute = 0;
            if (full.group(5) != null) {
                hour = Integer.parseInt(full.group(5));
                minute = full.group(6) == null ? 0 : Integer.parseInt(full.group(6));
                hour = adjustHour(hour, full.group(4));
            }
            return toMillis(date, hour, minute);
        }

        LocalDate date = null;
        Matcher rel = RELATIVE.matcher(s);
        if (rel.find()) {
            switch (rel.group(1)) {
                case "明天":
                case "明日":
                    date = today.plusDays(1);
                    break;
                case "后天":
                    date = today.plusDays(2);
                    break;
                case "大后天":
                    date = today.plusDays(3);
                    break;
                default:
                    date = today;
                    break;
            }
        }
        if (date == null) {
            Matcher md = MONTH_DAY.matcher(s);
            if (md.find()) {
                date = safeDate(today.getYear(), Integer.parseInt(md.group(1)),
                        Integer.parseInt(md.group(2)));
            }
        }
        if (date == null && (s.contains("考试") || s.contains("上课") || s.contains("截止")
                || s.contains("之前") || s.contains("周"))) {
            Matcher dot = MONTH_DAY_DOT.matcher(s);
            if (dot.find()) {
                date = safeDate(today.getYear(), Integer.parseInt(dot.group(1)),
                        Integer.parseInt(dot.group(2)));
            }
        }
        if (date == null) return 0;

        int hour = 9;
        int minute = 0;
        Matcher time = TIME.matcher(s);
        if (time.find()) {
            hour = Integer.parseInt(time.group(2));
            minute = time.group(3) == null ? 0 : Integer.parseInt(time.group(3));
            hour = adjustHour(hour, time.group(1));
        }
        if (hour == 24) {
            date = date.plusDays(1);
            hour = 0;
            minute = 0;
        }
        return toMillis(date, hour, minute);
    }

    public static String describe(long millis) {
        return millis <= 0 ? "" : Dates.dueFull(millis);
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private static int adjustHour(int hour, String suffix) {
        if (suffix == null) return hour;
        if (("下午".equals(suffix) || "晚上".equals(suffix)) && hour < 12) return hour + 12;
        if ("中午".equals(suffix) && hour < 11) return hour + 12;
        return hour;
    }

    private static long toMillis(LocalDate date, int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return 0;
        return LocalDateTime.of(date, LocalTime.of(hour, minute))
                .atZone(Dates.ZONE).toInstant().toEpochMilli();
    }
}
