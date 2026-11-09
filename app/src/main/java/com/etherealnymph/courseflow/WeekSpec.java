package com.etherealnymph.courseflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and formats "1-10, 12-17", "单周 1-3, 7-13" and "双周 4-10". */
public final class WeekSpec {

    private static final Pattern RANGE = Pattern.compile("(\\d{1,2})\\s*(?:[-–—~至]\\s*(\\d{1,2}))?");

    private WeekSpec() {}

    public static ArrayList<Store.WeekRange> parse(String raw) {
        ArrayList<Store.WeekRange> result = new ArrayList<>();
        if (raw == null) return result;
        String text = raw.trim()
                .replace('，', ',')
                .replace('、', ',')
                .replace('；', ',')
                .replace(';', ',')
                .replace("第", "")
                .replace("周次", "")
                .replace("周", " ");
        String parity = "all";
        for (String tokenRaw : text.split(",")) {
            String token = tokenRaw.trim();
            if (token.isEmpty()) continue;
            if (token.contains("每") || token.contains("全部")) parity = "all";
            if (token.contains("单")) parity = "odd";
            if (token.contains("双")) parity = "even";
            token = token.replace("单", "").replace("双", "").replace("每", "")
                    .replace("全部", "").trim();
            Matcher m = RANGE.matcher(token);
            boolean any = false;
            while (m.find()) {
                any = true;
                int a = Integer.parseInt(m.group(1));
                int b = m.group(2) == null ? a : Integer.parseInt(m.group(2));
                if (a > b) {
                    int t = a;
                    a = b;
                    b = t;
                }
                if (a < 1 || b > 60) {
                    throw new IllegalArgumentException("周次必须在 1–60 之间：" + tokenRaw.trim());
                }
                Store.WeekRange r = new Store.WeekRange();
                r.start = a;
                r.end = b;
                r.parity = parity;
                result.add(r);
            }
            if (!any) throw new IllegalArgumentException("无法识别周次：" + tokenRaw.trim());
        }
        return result;
    }

    public static ArrayList<Store.WeekRange> one(int start, int end, String parity) {
        ArrayList<Store.WeekRange> list = new ArrayList<>();
        Store.WeekRange r = new Store.WeekRange();
        r.start = start;
        r.end = end;
        r.parity = Store.normalizeParity(parity);
        list.add(r);
        return list;
    }

    public static String format(List<Store.WeekRange> ranges) {
        if (ranges == null || ranges.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        String current = "all";
        for (int i = 0; i < ranges.size(); i++) {
            Store.WeekRange r = ranges.get(i);
            String parity = Store.normalizeParity(r.parity);
            if (i > 0) sb.append(", ");
            if (!parity.equals(current)) {
                if ("odd".equals(parity)) sb.append("单周 ");
                else if ("even".equals(parity)) sb.append("双周 ");
                else sb.append("每周 ");
                current = parity;
            }
            if (r.start == r.end) sb.append(r.start);
            else sb.append(r.start).append("-").append(r.end);
        }
        return sb.toString();
    }

    public static boolean covers(List<Store.WeekRange> ranges, int week) {
        if (ranges == null) return false;
        for (Store.WeekRange r : ranges) {
            if (week < r.start || week > r.end) continue;
            if ("odd".equals(r.parity) && week % 2 == 0) continue;
            if ("even".equals(r.parity) && week % 2 == 1) continue;
            return true;
        }
        return false;
    }

    public static boolean overlaps(List<Store.WeekRange> a, List<Store.WeekRange> b) {
        for (int week = 1; week <= 60; week++) {
            if (covers(a, week) && covers(b, week)) return true;
        }
        return false;
    }

    public static int maxWeek(List<Store.WeekRange> ranges) {
        int max = 0;
        if (ranges != null) for (Store.WeekRange r : ranges) max = Math.max(max, r.end);
        return max;
    }

    public static String describe(List<Store.WeekRange> ranges) {
        String s = format(ranges);
        return s.isEmpty() ? "未设置周次" : s + " 周";
    }

    public static String stripForPreview(String markup) {
        if (markup == null) return "";
        String s = markup;
        s = s.replaceAll("!\\[[^\\]]*\\]\\(attachment:[^)]+\\)", "[图片]");
        s = s.replaceAll("\\[[^\\]]*\\]\\(attachment:[^)]+\\)", "[附件]");
        s = s.replaceAll("(?m)^#{1,3}\\s*", "");
        s = s.replaceAll("(?m)^>\\s?", "");
        s = s.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        s = s.replaceAll("\\*(.*?)\\*", "$1");
        s = s.replaceAll("__(.*?)__", "$1");
        s = s.replaceAll("~~(.*?)~~", "$1");
        s = s.replaceAll("==(.*?)==", "$1");
        s = s.replaceAll("\\[color=[^\\]]+\\](.*?)\\[/color\\]", "$1");
        s = s.replaceAll("\\[size=[^\\]]+\\](.*?)\\[/size\\]", "$1");
        s = s.replaceAll("\\{:\\s*(left|center|right)\\s*\\}", "");
        return s.trim();
    }
}
