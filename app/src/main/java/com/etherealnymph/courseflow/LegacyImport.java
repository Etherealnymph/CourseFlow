package com.etherealnymph.courseflow;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Importers for the raw timetable / todo JSON formats used by the user. */
public final class LegacyImport {

    private LegacyImport() {}

    public static Store.Timetable timetable(String text) throws Exception {
        JSONObject root = new JSONObject(text);
        JSONObject map = root.optJSONObject("timetable");
        if (map == null) map = root.optJSONObject("schedule");
        if (map == null) throw new IllegalArgumentException("文件里缺少 timetable 对象");

        Store.Timetable table = new Store.Timetable();
        table.name = root.optString("name", "2026–2027 第一学期");
        String term = root.optString("termStartDate", "2026-08-31");
        LocalDate start = Dates.parseDate(term);
        table.termStart = Dates.date(start == null ? LocalDate.of(2026, 8, 31) : Dates.mondayOf(start));

        Store.TimeSlot[] slots = semesterSlots();
        for (Store.TimeSlot slot : slots) table.timeSlots.add(slot);

        Map<String, Store.Course> byKey = new LinkedHashMap<>();
        int colorIndex = 0;
        for (int day = 1; day <= 7; day++) {
            JSONArray entries = map.optJSONArray(dayName(day));
            if (entries == null) continue;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject o = entries.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("课程", "").trim();
                if (name.isEmpty()) continue; // 停课等备注行
                String code = o.optString("课程代码", "").trim();
                String location = o.optString("地点", "").trim();
                List<String> teachers = splitTeachers(o.optString("教师", ""));
                int[] range = parsePeriods(o.optString("节次", ""));
                if (range == null) continue;
                String weeks = o.optString("周次", "").trim();
                if (weeks.isEmpty()) weeks = "1-20";
                ArrayList<Store.WeekRange> ranges;
                try {
                    ranges = WeekSpec.parse(weeks);
                } catch (Exception e) {
                    continue;
                }
                String key = name + "|" + code;
                Store.Course course = byKey.get(key);
                if (course == null) {
                    course = new Store.Course();
                    course.id = Store.newId("course");
                    course.name = name;
                    course.code = code;
                    course.location = location;
                    course.teachers = new ArrayList<>(teachers);
                    course.color = Palette.COURSE_COLORS[colorIndex++ % Palette.COURSE_COLORS.length];
                    byKey.put(key, course);
                } else {
                    if (course.location.isEmpty()) course.location = location;
                    if (course.teachers.isEmpty()) course.teachers.addAll(teachers);
                }
                Store.Session session = new Store.Session();
                session.day = day;
                session.slotId = slots[range[0]].id;
                session.endSlotId = range[1] == range[0] ? "" : slots[range[1]].id;
                session.weeks = ranges;
                session.startWeek = ranges.get(0).start;
                session.endWeek = ranges.get(ranges.size() - 1).end;
                session.parity = ranges.size() == 1 ? ranges.get(0).parity : "all";
                session.location = location;
                session.teachers = new ArrayList<>(teachers);
                mergeOrAdd(table, course, session);
            }
        }

        JSONArray extra = root.optJSONArray("extra_courses");
        if (extra != null) {
            for (int i = 0; i < extra.length(); i++) {
                JSONObject o = extra.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("课程", "").trim();
                if (name.isEmpty()) continue;
                List<String> teachers = splitTeachers(o.optString("教师", ""));
                if (teachers.isEmpty()) continue; // 常为已收录课程的重复行
                String location = o.optString("地点", "").trim();
                int day = parseDay(o.optString("星期", ""));
                int[] range = parsePeriods(o.optString("节次", ""));
                if (day < 1 || range == null) continue;
                String weeks = o.optString("周次", "1-20");
                ArrayList<Store.WeekRange> ranges;
                try {
                    ranges = WeekSpec.parse(weeks);
                } catch (Exception e) {
                    continue;
                }
                String key = name + "|" + o.optString("课程代码", "");
                Store.Course course = byKey.get(key);
                if (course == null) {
                    course = new Store.Course();
                    course.id = Store.newId("course");
                    course.name = name;
                    course.code = o.optString("课程代码", "");
                    course.location = location;
                    course.teachers = new ArrayList<>(teachers);
                    course.color = Palette.COURSE_COLORS[colorIndex++ % Palette.COURSE_COLORS.length];
                    byKey.put(key, course);
                }
                Store.Session session = new Store.Session();
                session.day = day;
                session.slotId = slots[range[0]].id;
                session.endSlotId = range[1] == range[0] ? "" : slots[range[1]].id;
                session.weeks = ranges;
                session.startWeek = ranges.get(0).start;
                session.endWeek = ranges.get(ranges.size() - 1).end;
                session.parity = ranges.size() == 1 ? ranges.get(0).parity : "all";
                session.location = location;
                session.teachers = new ArrayList<>(teachers);
                mergeOrAdd(table, course, session);
            }
        }

        table.courses.addAll(byKey.values());
        if (table.courses.isEmpty()) throw new IllegalArgumentException("没有解析到任何课程");
        return table;
    }

    public static ArrayList<Store.Todo> todos(String text) throws Exception {
        JSONObject root = new JSONObject(text);
        JSONArray list = root.optJSONArray("todo_list");
        if (list == null) list = root.optJSONArray("todos");
        if (list == null) throw new IllegalArgumentException("文件里缺少 todo_list 数组");
        ArrayList<Store.Todo> result = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            String title = o.optString("title", "").trim();
            String category = o.optString("category", "").trim();
            if (title.isEmpty() && category.isEmpty()) continue;
            if (title.isEmpty()) title = category;

            Store.Todo t = new Store.Todo();
            t.id = Store.newId("todo");
            t.title = title;
            t.createdAt = System.currentTimeMillis();
            StringBuilder note = new StringBuilder();
            String details = o.optString("details", "");
            String content = o.optString("content", "");
            String items = o.optString("items", "");
            if (!details.isEmpty()) note.append(details);
            if (!content.isEmpty()) {
                if (note.length() > 0) note.append("\n\n");
                note.append(content);
            }
            if (!items.isEmpty()) {
                if (note.length() > 0) note.append("\n\n");
                note.append(items);
            }
            String original = o.optString("original_schedule", "");
            String adjusted = o.optString("adjusted_schedule", "");
            if (!original.isEmpty()) note.append("\n\n原安排：").append(original);
            if (!adjusted.isEmpty()) note.append("\n调整：").append(adjusted);
            t.note = note.toString().trim();

            String timestamp = o.optString("timestamp", "").trim();
            String deadline = o.optString("deadline", "").trim();
            long at = TimeTextParser.parse(timestamp);
            if (at == 0) at = TimeTextParser.parse(deadline);
            if (at == 0) at = TimeTextParser.parse(t.title + " " + t.note);
            t.dueAt = at;
            t.priority = ("已过期".equals(o.optString("status", ""))) ? 1 : 0;
            result.add(t);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("todo_list 为空");
        return result;
    }

    private static void mergeOrAdd(Store.Timetable table, Store.Course course,
                                   Store.Session candidate) {
        int candidateStart = Store.slotIndex(table, candidate.slotId);
        int candidateEnd = Store.sessionEndIndex(table, candidate);
        String candidateWeeks = WeekSpec.format(candidate.effectiveWeeks());
        for (Store.Session existing : course.sessions) {
            if (existing.day != candidate.day) continue;
            if (!existing.location.equals(candidate.location)) continue;
            if (!existing.teachers.equals(candidate.teachers)) continue;
            if (!WeekSpec.format(existing.effectiveWeeks()).equals(candidateWeeks)) continue;
            int existingEnd = Store.sessionEndIndex(table, existing);
            if (existingEnd + 1 == candidateStart) {
                existing.endSlotId = table.timeSlots.get(candidateEnd).id;
                return;
            }
        }
        if (!duplicate(course, candidate)) course.sessions.add(candidate);
    }

    private static boolean duplicate(Store.Course course, Store.Session candidate) {
        for (Store.Session s : course.sessions) {
            if (s.day != candidate.day) continue;
            if (!s.slotId.equals(candidate.slotId)) continue;
            if (!WeekSpec.overlaps(s.effectiveWeeks(), candidate.effectiveWeeks())) continue;
            return true;
        }
        return false;
    }

    private static List<String> splitTeachers(String text) {
        ArrayList<String> list = new ArrayList<>();
        if (text == null) return list;
        for (String s : text.split("[,，、;；/]")) {
            if (!s.trim().isEmpty()) list.add(s.trim());
        }
        return list;
    }

    private static int parseDay(String text) {
        if (text == null) return 0;
        if (text.contains("一")) return 1;
        if (text.contains("二")) return 2;
        if (text.contains("三")) return 3;
        if (text.contains("四")) return 4;
        if (text.contains("五")) return 5;
        if (text.contains("六")) return 6;
        if (text.contains("日") || text.contains("天")) return 7;
        return 0;
    }

    private static String dayName(int day) {
        String[] names = {"", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        return names[day];
    }

    private static int[] parsePeriods(String text) {
        if (text == null) return null;
        String s = text.replace("第", "").replace("节", "").replace(" ", "");
        int dash = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-' || c == '–' || c == '—' || c == '~' || c == '至') {
                dash = i;
                break;
            }
        }
        try {
            int a;
            int b;
            if (dash < 0) {
                a = parseSmallNumber(s);
                b = a;
            } else {
                a = parseSmallNumber(s.substring(0, dash));
                b = parseSmallNumber(s.substring(dash + 1));
            }
            if (a < 1 || b < 1) return null;
            a = Math.min(11, a);
            b = Math.min(11, Math.max(a, b));
            return new int[]{a - 1, b - 1};
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseSmallNumber(String text) {
        if (text == null || text.isEmpty()) return -1;
        if (text.matches("\\d+")) return Integer.parseInt(text);
        int result = 0;
        boolean ten = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int n = "零一二三四五六七八九".indexOf(c);
            if (c == '十') {
                ten = true;
                if (result == 0) result = 1;
                result *= 10;
            } else if (n >= 0) {
                if (ten && result % 10 == 0) result += n;
                else if (ten) result += n;
                else result = n;
            }
        }
        return result;
    }

    public static Store.TimeSlot[] semesterSlots() {
        int[][] times = {
                {8, 30, 9, 15}, {9, 20, 10, 5}, {10, 20, 11, 5}, {11, 20, 11, 55},
                {14, 30, 15, 15}, {15, 20, 16, 5}, {16, 20, 17, 5}, {17, 10, 17, 55},
                {19, 30, 20, 15}, {20, 20, 21, 5}, {21, 10, 21, 55}
        };
        Store.TimeSlot[] slots = new Store.TimeSlot[times.length];
        for (int i = 0; i < times.length; i++) {
            Store.TimeSlot slot = new Store.TimeSlot();
            slot.id = Store.newId("slot");
            slot.name = "第 " + (i + 1) + " 节";
            slot.segments.add(new Store.Segment(Dates.time(times[i][0] * 60 + times[i][1]),
                    Dates.time(times[i][2] * 60 + times[i][3])));
            slots[i] = slot;
        }
        return slots;
    }
}
