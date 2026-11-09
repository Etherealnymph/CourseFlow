package com.etherealnymph.courseflow;

import android.content.Context;
import android.graphics.Color;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A deliberately small, file-backed store. Everything lives in one readable JSON
 * document under filesDir; exports use the same structures so they round-trip.
 */
public final class Store {

    public static final String APP_SCHEMA = "courseflow.app";
    public static final String TIMETABLE_SCHEMA = "courseflow.timetable";
    public static final String NOTES_SCHEMA = "courseflow.notes";

    private static Store instance;

    private final Context context;
    private final File file;
    private final boolean demoMode;
    public Data data;

    public static synchronized Store get(Context context) {
        if (instance == null) instance = new Store(context.getApplicationContext());
        return instance;
    }

    private Store(Context context) {
        this.context = context;
        this.demoMode = context.getResources().getBoolean(R.bool.demo_mode);
        this.file = new File(context.getFilesDir(),
                demoMode ? "courseflow_demo.json" : "courseflow.json");
        load();
    }

    public boolean isDemoMode() {
        return demoMode;
    }

    // ------------------------------------------------------------------ data

    public static class Data {
        public String theme = "system";
        public long createdAt = System.currentTimeMillis();
        public Timetable timetable = new Timetable();
        public ArrayList<Note> notes = new ArrayList<>();
        public ArrayList<Todo> todos = new ArrayList<>();
        public ArrayList<Holiday> holidays = new ArrayList<>();
        public ArrayList<Alarm> alarms = new ArrayList<>();
        public ArrayList<Recording> recordings = new ArrayList<>();
        public String recordingDir = "Music/时屿";
        public boolean alarmsSeeded = false;
        public Vault vault;
    }

    public static class Timetable {
        public String name = "我的课表";
        public String termStart = Dates.date(Dates.mondayOf(Dates.today()));
        public ArrayList<TimeSlot> timeSlots = new ArrayList<>();
        public ArrayList<Course> courses = new ArrayList<>();
    }

    public static class TimeSlot {
        public String id = "";
        public String name = "";
        public ArrayList<Segment> segments = new ArrayList<>();

        public int startMinutes() {
            int best = 24 * 60;
            for (Segment s : segments) best = Math.min(best, Dates.minutes(s.start));
            return best == 24 * 60 ? 0 : best;
        }

        public int endMinutes() {
            int best = 0;
            for (Segment s : segments) best = Math.max(best, Dates.minutes(s.end));
            return best;
        }

        public String span() {
            if (segments.isEmpty()) return "";
            return segments.get(0).start + "–" + segments.get(segments.size() - 1).end;
        }

        public String segmentLabel() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segments.size(); i++) {
                if (i > 0) sb.append(" · ");
                sb.append(segments.get(i).start).append("–").append(segments.get(i).end);
            }
            return sb.toString();
        }
    }

    public static class Segment {
        public String start = "08:00";
        public String end = "09:40";

        public Segment() {}

        public Segment(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }

    public static class Course {
        public String id = "";
        public String name = "";
        public String code = "";
        public String location = "";
        public ArrayList<String> teachers = new ArrayList<>();
        public ArrayList<Session> sessions = new ArrayList<>();
        public int color = Palette.COURSE_COLORS[0];

        public String teacherLine() {
            if (teachers == null || teachers.isEmpty()) return "";
            return String.join(" · ", teachers);
        }
    }

    public static class WeekRange {
        public int start = 1;
        public int end = 1;
        public String parity = "all";

        public WeekRange() {}

        public WeekRange(int start, int end, String parity) {
            this.start = start;
            this.end = end;
            this.parity = parity;
        }
    }

    public static class Session {
        public int day = 1;
        public String slotId = "";
        public String endSlotId = "";
        /** Empty means fall back to the course-level location. */
        public String location = "";
        /** Empty means fall back to the course-level teachers. */
        public ArrayList<String> teachers = new ArrayList<>();
        public ArrayList<WeekRange> weeks = new ArrayList<>();
        public int startWeek = 1;
        public int endWeek = 20;
        public String parity = "all";

        public ArrayList<WeekRange> effectiveWeeks() {
            if (weeks != null && !weeks.isEmpty()) return weeks;
            ArrayList<WeekRange> fallback = new ArrayList<>();
            WeekRange r = new WeekRange();
            r.start = startWeek;
            r.end = endWeek;
            r.parity = parity == null ? "all" : parity;
            fallback.add(r);
            return fallback;
        }
    }

    public static class Attachment {
        public String id = "";
        public String type = "file"; // image | audio | file
        public String name = "";
        public String path = "";     // app-private file path
        public String uri = "";      // persistable content URI for file shortcuts
        public String mime = "";
        public long size;
        public long createdAt = System.currentTimeMillis();
    }

    public static class Note {
        public String id = "";
        public String title = "";
        public String body = "";
        public long createdAt = System.currentTimeMillis();
        public long updatedAt = System.currentTimeMillis();
        public boolean pinned;
        public int color = Palette.NOTE_COLORS[0];
        public ArrayList<Attachment> attachments = new ArrayList<>();
    }

    public static class Todo {
        public String id = "";
        public String title = "";
        public String note = "";
        public long remindAt;
        public long dueAt;
        public boolean done;
        public long createdAt = System.currentTimeMillis();
        public long doneAt;
        public int priority;
        public int advanceMinutes = 120;
        public boolean advanceAlarm;
        public ArrayList<Attachment> attachments = new ArrayList<>();
    }

    public static class Holiday {
        public String date = "";
        public String name = "";
        /** false = public holiday, true = make-up workday. */
        public boolean workday;
    }

    public static class Alarm {
        public String id = "";
        public String label = "";
        public int hour = 7;
        public int minute = 30;
        public boolean enabled = true;
        /** workday | offday | custom */
        public String mode = "workday";
        /** bit 0 = Monday ... bit 6 = Sunday. */
        public int weekdays = 0x1F;
        public String startDate = "";
        public String endDate = "";
        public ArrayList<String> extraDates = new ArrayList<>();
        public int repeatCount = 1;
        public int repeatIntervalMin = 5;
        public int ringSeconds = 30;
        public boolean vibrate = false;
        public long createdAt = System.currentTimeMillis();
    }

    public static class Recording {
        public String id = "";
        public String name = "";
        public String uri = "";
        public long durationMs = 0;
        public long size = 0;
        public long createdAt = System.currentTimeMillis();
    }

    public static class Vault {
        public String salt = "";
        public String verifierIv = "";
        public String verifierCipher = "";
        public ArrayList<PrivateNote> notes = new ArrayList<>();
    }

    public static class PrivateNote {
        public String id = "";
        public String iv = "";
        public String cipher = "";
        public long updatedAt = System.currentTimeMillis();
    }

    public static class NotesBundle {
        public final ArrayList<Note> notes = new ArrayList<>();
        public final ArrayList<Todo> todos = new ArrayList<>();
        public Vault vault;
        public String exportedAt = "";
    }

    public static class ImportResult {
        public int notes;
        public int todos;
        public boolean privateSkipped;
        public boolean privateImported;
        public boolean privateKeptLocked;
    }

    // ------------------------------------------------------------- file load

    private void load() {
        data = null;
        if (file.exists()) {
            try {
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                data = parseApp(new JSONObject(text));
            } catch (Exception ignored) {
                File broken = new File(context.getFilesDir(), "courseflow.broken.json");
                //noinspection ResultOfMethodCallIgnored
                file.renameTo(broken);
                data = null;
            }
        }
        if (data == null) {
            data = demoMode ? demoSeedData() : seedData();
            save();
        } else {
            ensureDefaults(data);
        }
    }

    private void ensureDefaults(Data d) {
        if (d.theme == null) d.theme = "system";
        if (d.createdAt <= 0) d.createdAt = System.currentTimeMillis();
        if (d.timetable == null) d.timetable = new Timetable();
        if (d.notes == null) d.notes = new ArrayList<>();
        if (d.todos == null) d.todos = new ArrayList<>();
        if (d.holidays == null) d.holidays = new ArrayList<>();
        if (d.alarms == null) d.alarms = new ArrayList<>();
        if (d.recordings == null) d.recordings = new ArrayList<>();
        if (d.recordingDir == null || d.recordingDir.trim().isEmpty()) {
            d.recordingDir = "Music/时屿";
        }
        if (!d.alarmsSeeded) {
            d.alarms.addAll(defaultAlarms());
            d.alarmsSeeded = true;
        }
        if (d.timetable.timeSlots == null) d.timetable.timeSlots = new ArrayList<>();
        if (d.timetable.courses == null) d.timetable.courses = new ArrayList<>();
        if (d.timetable.termStart == null || Dates.parseDate(d.timetable.termStart) == null) {
            d.timetable.termStart = Dates.date(Dates.mondayOf(Dates.today()));
        }
        for (Course c : d.timetable.courses) {
            if (c.teachers == null) c.teachers = new ArrayList<>();
            if (c.sessions == null) c.sessions = new ArrayList<>();
            if (c.code == null) c.code = "";
            if (c.id == null || c.id.isEmpty()) c.id = newId("course");
            if (c.color == 0) c.color = Palette.COURSE_COLORS[0];
            for (Session session : c.sessions) normalizeSession(session);
        }
        for (TimeSlot s : d.timetable.timeSlots) {
            if (s.segments == null) s.segments = new ArrayList<>();
            if (s.id == null || s.id.isEmpty()) s.id = newId("slot");
        }
        // Migrate the previous built-in demo (6 large periods, 7 sample
        // courses) to the real 2026–2027 timetable with 11 periods.
        if (d.timetable.timeSlots.size() == 6 && !d.timetable.courses.isEmpty()
                && d.timetable.timeSlots.get(0).name.contains("1–2")
                && d.timetable.timeSlots.get(1).name.contains("3–4")) {
            boolean oldDemo = false;
            for (Course c : d.timetable.courses) {
                if ("数据结构".equals(c.name) || "高等数学".equals(c.name)) {
                    oldDemo = true;
                    break;
                }
            }
            if (oldDemo) d.timetable = seedData().timetable;
        }
        for (Note n : d.notes) if (n.attachments == null) n.attachments = new ArrayList<>();
        for (Todo t : d.todos) {
            if (t.attachments == null) t.attachments = new ArrayList<>();
        }
        save();
    }

    private static void normalizeSession(Session s) {
        if (s.teachers == null) s.teachers = new ArrayList<>();
        if (s.location == null) s.location = "";
        if (s.endSlotId == null) s.endSlotId = "";
        if (s.parity == null) s.parity = "all";
        if (s.weeks == null) s.weeks = new ArrayList<>();
        if (s.weeks.isEmpty()) {
            WeekRange r = new WeekRange();
            r.start = s.startWeek <= 0 ? 1 : s.startWeek;
            r.end = s.endWeek < r.start ? r.start : s.endWeek;
            r.parity = s.parity;
            s.weeks.add(r);
        }
        s.startWeek = s.weeks.get(0).start;
        s.endWeek = s.weeks.get(s.weeks.size() - 1).end;
        s.parity = s.weeks.size() == 1 ? s.weeks.get(0).parity : "all";
    }

    public static String effectiveLocation(Course course, Session session) {
        if (session != null && session.location != null && !session.location.trim().isEmpty()) {
            return session.location;
        }
        return course == null || course.location == null ? "" : course.location;
    }

    public static ArrayList<String> effectiveTeachers(Course course, Session session) {
        if (session != null && session.teachers != null && !session.teachers.isEmpty()) {
            return session.teachers;
        }
        if (course == null || course.teachers == null) return new ArrayList<>();
        return course.teachers;
    }

    public static String teacherLine(Course course, Session session) {
        ArrayList<String> list = effectiveTeachers(course, session);
        return list.isEmpty() ? "" : String.join(" · ", list);
    }

    public synchronized void save() {
        try {
            JSONObject root = appToJson(data);
            byte[] bytes = root.toString(2).getBytes(StandardCharsets.UTF_8);
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(bytes);
                out.getFD().sync();
            }
            if (file.exists() && !file.delete()) {
                // Continue: renameTo below still replaces on most Linux file systems.
            }
            if (!tmp.renameTo(file)) {
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(bytes);
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception e) {
            android.util.Log.e("CourseFlow", "save failed", e);
        }
    }

    // --------------------------------------------------------------- seeding

    public static ArrayList<Alarm> defaultAlarms() {
        ArrayList<Alarm> list = new ArrayList<>();
        list.add(defaultAlarm("早八", 8, 0, "workday"));
        list.add(defaultAlarm("午后", 14, 0, "workday"));
        list.add(defaultAlarm("休息日早", 9, 0, "offday"));
        list.add(defaultAlarm("休息日午后", 14, 30, "offday"));
        return list;
    }

    private static Alarm defaultAlarm(String label, int hour, int minute, String mode) {
        Alarm a = new Alarm();
        a.id = newId("alarm");
        a.label = label;
        a.hour = hour;
        a.minute = minute;
        a.mode = mode;
        a.enabled = true;
        a.weekdays = 0x1F;
        return a;
    }

    private Data demoSeedData() {
        Data d = new Data();
        d.createdAt = System.currentTimeMillis();
        d.timetable.name = "2026 秋季示例课表";
        d.timetable.termStart = "2026-08-31";

        TimeSlot p1 = slot("第 1 节", 8, 30, 9, 15);
        TimeSlot p2 = slot("第 2 节", 9, 20, 10, 5);
        TimeSlot p3 = slot("第 3 节", 10, 20, 11, 5);
        TimeSlot p4 = slot("第 4 节", 11, 20, 11, 55);
        TimeSlot p5 = slot("第 5 节", 14, 30, 15, 15);
        TimeSlot p6 = slot("第 6 节", 15, 20, 16, 5);
        TimeSlot p7 = slot("第 7 节", 16, 20, 17, 5);
        TimeSlot p8 = slot("第 8 节", 17, 10, 17, 55);
        TimeSlot p9 = slot("第 9 节", 19, 30, 20, 15);
        TimeSlot p10 = slot("第 10 节", 20, 20, 21, 5);
        TimeSlot p11 = slot("第 11 节", 21, 10, 21, 55);
        Collections.addAll(d.timetable.timeSlots, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11);

        addCourse(d, "MATH1001", "高等数学", "教学楼A201", new String[]{"李明"}, 0,
                sess(1, p1, p2, "1-16", "教学楼A201", "李明"),
                sess(3, p3, p4, "1-16", "教学楼A201", "李明"));
        addCourse(d, "CS1002", "数据结构与算法", "实验楼B305", new String[]{"王芳"}, 1,
                sess(2, p3, p4, "1-16", "实验楼B305", "王芳"),
                sess(4, p5, p6, "1-16", "实验楼B305", "王芳"));
        addCourse(d, "ENG1003", "大学英语", "外语楼C102", new String[]{"Sarah"}, 2,
                sess(2, p1, p2, "1-16", "外语楼C102", "Sarah"));
        addCourse(d, "PHY1004", "大学物理", "教学楼C110", new String[]{"陈老师"}, 3,
                sess(4, p1, p2, "1-16", "教学楼C110", "陈老师"));
        addCourse(d, "PE1005", "体育 · 羽毛球", "东区体育馆", new String[]{"刘教练"}, 4,
                sess(5, p7, p8, "1-16", "东区体育馆", "刘教练"));

        long now = System.currentTimeMillis();
        Note n1 = new Note();
        n1.id = newId("note");
        n1.title = "欢迎使用时屿课程表";
        n1.body = "# 一周的安排，从这里开始\n\n"
                + "这是一份**示例笔记**。你可以直接修改、删除，或者新建自己的笔记。\n\n"
                + "> 选中文字后，点工具栏就能加粗、高亮、改颜色。\n\n"
                + "| 功能 | 说明 |\n|---|---|\n| 课表 | 7 天 × 11 节 |\n| 待办 | 提前提醒 + 截止提醒 |\n| 笔记 | 图片、录音、表格 |\n\n"
                + "- [x] 打开课表看看\n- [ ] 新建一篇自己的笔记";
        n1.pinned = true;
        n1.color = Palette.NOTE_COLORS[0];
        n1.createdAt = now - 60_000L * 30;
        n1.updatedAt = now;
        d.notes.add(n1);

        Note n2 = new Note();
        n2.id = newId("note");
        n2.title = "示例：待办与提醒";
        n2.body = "## 三种提醒\n\n1. **开始提醒**：自定义开始时间。\n2. **提前提醒**：默认提前 2 小时。\n3. **截止提醒**：到截止时间再提醒一次。\n\n提前提醒可以单独选择是否响铃。";
        n2.color = Palette.NOTE_COLORS[2];
        n2.createdAt = now - 60_000L * 20;
        n2.updatedAt = now;
        d.notes.add(n2);

        Todo t1 = todo("提交课程作业", "示例待办：默认提前 2 小时提醒。",
                Dates.at(Dates.today().plusDays(1), 20, 0), 1);
        d.todos.add(t1);
        Todo t2 = todo("阅读示例笔记", "", Dates.at(Dates.today().plusDays(2), 12, 0), 0);
        d.todos.add(t2);
        Todo t3 = todo("整理本周计划", "可以在这里写备注、插图片或录音。", 0, 0);
        d.todos.add(t3);

        d.alarms.addAll(defaultAlarms());
        d.alarmsSeeded = true;
        return d;
    }

    private Data seedData() {
        Data d = new Data();
        d.createdAt = System.currentTimeMillis();
        d.timetable.name = "2026–2027 第一学期";
        d.timetable.termStart = "2026-08-31";

        TimeSlot p1 = slot("第 1 节", 8, 30, 9, 15);
        TimeSlot p2 = slot("第 2 节", 9, 20, 10, 5);
        TimeSlot p3 = slot("第 3 节", 10, 20, 11, 5);
        TimeSlot p4 = slot("第 4 节", 11, 20, 11, 55);
        TimeSlot p5 = slot("第 5 节", 14, 30, 15, 15);
        TimeSlot p6 = slot("第 6 节", 15, 20, 16, 5);
        TimeSlot p7 = slot("第 7 节", 16, 20, 17, 5);
        TimeSlot p8 = slot("第 8 节", 17, 10, 17, 55);
        TimeSlot p9 = slot("第 9 节", 19, 30, 20, 15);
        TimeSlot p10 = slot("第 10 节", 20, 20, 21, 5);
        TimeSlot p11 = slot("第 11 节", 21, 10, 21, 55);
        TimeSlot[] slots = {p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11};
        Collections.addAll(d.timetable.timeSlots, slots);

        addCourse(d, "G2700940.03", "电磁场理论", "品学楼C203", new String[]{"陈益凯"}, 0,
                sess(1, p1, p2, "1-10, 12-17", "品学楼C203", "陈益凯"),
                sess(1, p3, p3, "15", "品学楼C216", "陈益凯"),
                sess(4, p3, p4, "1-4, 6-9, 11-17", "品学楼C203", "陈益凯"));
        addCourse(d, "G2700635.01", "信号与系统", "品学楼C105", new String[]{"杨建宇"}, 1,
                sess(1, p3, p4, "1-14", "品学楼C105", "杨建宇"),
                sess(3, p3, p4, "1-14", "品学楼C105", "杨建宇"));
        addCourse(d, "D2701345.01", "随机数学（含概率论、数理统计、随机过程）", "品学楼A211",
                new String[]{"高晴"}, 2,
                sess(1, p5, p6, "2-15", "品学楼A211", "高晴"),
                sess(2, p5, p6, "4-15", "品学楼A211", "高晴"),
                sess(5, p3, p4, "单周 1-3, 7-13", "品学楼A211", "高晴"));
        addCourse(d, "G3102160.01", "半导体器件与物理（高阶课程）", "品学楼C404-A",
                new String[]{"刘志伟", "蒋书文"}, 3,
                sess(1, p7, p7, "10-17", "品学楼C404-A", "刘志伟"),
                sess(1, p7, p8, "1-9", "品学楼C404-A", "蒋书文"),
                sess(3, p1, p2, "9-16", "品学楼C404-A", "刘志伟"),
                sess(3, p1, p2, "1-8", "品学楼C404-A", "蒋书文"),
                sess(4, p5, p6, "9-16", "品学楼C404-A", "刘志伟"),
                sess(4, p5, p6, "1-4, 6-8", "品学楼C404-A", "蒋书文"),
                sess(6, p5, p6, "11", "品学楼C404-A", "蒋书文"));
        addCourse(d, "A0110620.01", "电子工程设计之微处理器最小系统设计", "品学楼A103",
                new String[]{"胡全"}, 4,
                sess(1, p9, p10, "1-16", "品学楼A103", "胡全"));
        addCourse(d, "A6206315.01", "专业写作基础", "品学楼C210", new String[]{"苏元章"}, 5,
                sess(2, p1, p2, "1-8", "品学楼C210", "苏元章"),
                sess(6, p1, p4, "12", "品学楼C210", "苏元章"),
                sess(7, p5, p8, "11", "品学楼C210", "苏元章"));
        addCourse(d, "P2700340.03", "大学物理II", "品学楼A207", new String[]{"高雅纯"}, 6,
                sess(2, p3, p4, "1-3, 5-17", "品学楼A207", "高雅纯"),
                sess(4, p1, p2, "1-4, 6-16", "品学楼A207", "高雅纯"));
        addCourse(d, "A1511520.02", "经济学概论", "品学楼C202", new String[]{"刘磊"}, 7,
                sess(2, p9, p10, "1-11", "品学楼C202", "刘磊"));
        addCourse(d, "G0803435.09", "数据结构与算法II", "品学楼A304", new String[]{"戴波"}, 0,
                sess(3, p5, p6, "1-15", "品学楼A304", "戴波"),
                sess(5, p1, p2, "1-3, 6-15", "品学楼A304", "戴波"));
        addCourse(d, "B2071910.13", "羽毛球C", "", new String[]{"马强"}, 1,
                sess(3, p7, p8, "3-18", "", "马强"));
        addCourse(d, "M1800910.04", "改革开放史专题讲座", "品学楼A102", new String[]{"聂小雄"}, 2,
                sess(3, p9, p10, "1-6", "品学楼A102", "聂小雄"));
        addCourse(d, "E0400120.02", "现代工程设计制图", "品学楼C315", new String[]{"余思佳"}, 3,
                sess(4, p7, p8, "1-4, 6-17", "品学楼C315", "余思佳"),
                sess(6, p7, p8, "11", "基础实验大楼A537", "余思佳"));
        addCourse(d, "S1214710.88", "大学物理实验 I", "物电学院实验室1", new String[]{"陈中钧"}, 4,
                sess(4, p9, p10, "双周 4-10", "物电学院实验室1", "陈中钧"));
        addCourse(d, "S3100240.01", "ECE进阶式挑战性项目课一 “能动感存算”集成智能微系统",
                "6号科研楼A666", new String[]{"谢小东"}, 5,
                sess(5, p5, p6, "1-16", "6号科研楼A666", "谢小东"));
        addCourse(d, "M1800220.J4", "形势与政策", "品学楼A310", new String[]{"吴钊阳", "刘今杰"}, 6,
                sess(7, p1, p2, "15", "品学楼A310", "吴钊阳", "刘今杰"));
        addCourse(d, "", "数学竞赛精讲", "品学楼A209", new String[]{"高中喜"}, 7,
                sess(6, p1, p8, "2-3, 7-10", "品学楼A209", "高中喜"));

        long now = System.currentTimeMillis();
        Note n1 = new Note();
        n1.id = newId("note");
        n1.title = "半导体器件与物理 · 复习";
        n1.body = "# 第 7 章 场效应晶体管\n\n**重点**：阈值电压、沟道调制、短沟道效应。\n\n> 蒋书文老师的课堂强调：先画能带，再算电荷。\n\n- [x] 整理 1–8 周笔记\n- [ ] 完成习题 7.3 / 7.8\n\n| 参数 | 公式 |\n|---|---|\n| 阈值电压 | Vth = ... |\n| 跨导 | gm = ... |";
        n1.pinned = true;
        n1.color = Palette.NOTE_COLORS[0];
        n1.createdAt = now - 1000L * 60 * 60 * 40;
        n1.updatedAt = now - 1000L * 60 * 60 * 3;
        d.notes.add(n1);

        Note n2 = new Note();
        n2.id = newId("note");
        n2.title = "ECE 项目课 · 本周讨论";
        n2.body = "## 能动感存算\n\n{: center}**周六前把最小系统框图发到群里**{: center}\n\n1. 电源与时钟树\n2. 传感器接口\n3. 无线回传\n\n> 6 号科研楼 A666，周五 15:20";
        n2.color = Palette.NOTE_COLORS[1];
        n2.createdAt = now - 1000L * 60 * 60 * 60;
        n2.updatedAt = now - 1000L * 60 * 60 * 12;
        d.notes.add(n2);

        Note n3 = new Note();
        n3.id = newId("note");
        n3.title = "电路与电子技术 · 知识点";
        n3.body = "第一基本量 / 拉梅系数 / 正弦波发生器畸变\n三极管小信号电阻 / 切比雪夫、椭圆、巴特沃斯滤波器\nfliege 电路 / 曲面第一基本形式 / NE555 音乐\n多谐振荡器 / 双稳态电路 / 环路震荡器";
        n3.color = Palette.NOTE_COLORS[2];
        n3.createdAt = now - 1000L * 60 * 60 * 20;
        n3.updatedAt = now - 1000L * 60 * 60 * 18;
        d.notes.add(n3);

        Todo t1 = todo("练习册 P30-31", "", Dates.at(LocalDate.of(2026, 11, 9), 0, 0), 1);
        d.todos.add(t1);
        Todo t2 = todo("提交调停课申请：G2700940.03 电磁场理论",
                "班级: 2025270901 2025270902 2025270903 2025270904 2025270905, 英才实验学院, 陈益凯老师\n原安排：星期一 1-2 [第11周] [11月09日] 品学楼C203\n调整：星期一 3-4 [第15周] [12月07日] 品学楼C216",
                Dates.at(LocalDate.of(2026, 11, 9), 16, 15), 2);
        d.todos.add(t2);
        Todo t3 = todo("期中考试", "定于11月14日（周六）下午2点-4点，考点：品学楼A101，请携带科学计算器！@所有人",
                Dates.at(LocalDate.of(2026, 11, 14), 14, 0), 2);
        d.todos.add(t3);
        Todo t4 = todo("随机数学随堂考试安排",
                "@全体成员 随机数学期中考试安排在11.10（11周二）在上课时间和教室进行随堂考试，时间是一个半小时，内容为前三章。",
                Dates.at(LocalDate.of(2026, 11, 10), 12, 30), 2);
        d.todos.add(t4);
        Todo t5 = todo("半物考试", "", Dates.at(LocalDate.of(2026, 11, 9), 12, 30), 1);
        d.todos.add(t5);
        Todo t6 = todo("大学物理考试", "明天 12:30", 0, 0);
        d.todos.add(t6);
        Todo t7 = todo("架构，需求描述", "", 0, 0);
        d.todos.add(t7);
        d.alarms.addAll(defaultAlarms());
        d.alarmsSeeded = true;
        return d;
    }

    private void addCourse(Data d, String code, String name, String location, String[] teachers,
                           int colorIndex, Session... sessions) {
        Course c = new Course();
        c.id = newId("course");
        c.code = code;
        c.name = name;
        c.location = location;
        Collections.addAll(c.teachers, teachers);
        Collections.addAll(c.sessions, sessions);
        c.color = Palette.COURSE_COLORS[colorIndex % Palette.COURSE_COLORS.length];
        d.timetable.courses.add(c);
    }

    private TimeSlot slot(String name, int h1, int m1, int h2, int m2) {
        TimeSlot s = new TimeSlot();
        s.id = newId("slot");
        s.name = name;
        s.segments.add(new Segment(Dates.time(h1 * 60 + m1), Dates.time(h2 * 60 + m2)));
        return s;
    }

    private Session sess(int day, TimeSlot start, TimeSlot end, String weeks, String location,
                         String... teachers) {
        Session s = new Session();
        s.day = day;
        s.slotId = start.id;
        s.endSlotId = end.id.equals(start.id) ? "" : end.id;
        s.weeks = WeekSpec.parse(weeks);
        s.startWeek = s.weeks.get(0).start;
        s.endWeek = s.weeks.get(s.weeks.size() - 1).end;
        s.parity = s.weeks.size() == 1 ? s.weeks.get(0).parity : "all";
        s.location = location == null ? "" : location;
        Collections.addAll(s.teachers, teachers);
        return s;
    }

    private Todo todo(String title, String note, long dueAt, int priority) {
        Todo t = new Todo();
        t.id = newId("todo");
        t.title = title;
        t.note = note;
        t.dueAt = dueAt;
        t.priority = priority;
        return t;
    }

    // ------------------------------------------------------------ app json

    public static JSONObject appToJson(Data d) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema", APP_SCHEMA);
        root.put("version", 2);
        root.put("theme", d.theme);
        root.put("createdAt", d.createdAt);
        root.put("timetable", timetableToJson(d.timetable));
        root.put("notes", notesToJson(d.notes));
        root.put("todos", todosToJson(d.todos));
        root.put("holidays", holidaysToJson(d.holidays));
        root.put("alarms", alarmsToJson(d.alarms));
        root.put("recordings", recordingsToJson(d.recordings));
        root.put("recordingDir", d.recordingDir);
        root.put("alarmsSeeded", d.alarmsSeeded);
        if (d.vault != null) root.put("vault", vaultToJson(d.vault));
        return root;
    }

    private static Data parseApp(JSONObject root) throws JSONException {
        Data d = new Data();
        d.theme = root.optString("theme", "system");
        d.createdAt = root.optLong("createdAt", System.currentTimeMillis());
        JSONObject tt = root.optJSONObject("timetable");
        d.timetable = tt == null ? new Timetable() : parseTimetable(tt);
        d.notes = parseNotes(root.optJSONArray("notes"));
        d.todos = parseTodos(root.optJSONArray("todos"));
        d.holidays = parseHolidays(root.optJSONArray("holidays"));
        d.alarms = parseAlarms(root.optJSONArray("alarms"));
        d.recordings = parseRecordings(root.optJSONArray("recordings"));
        d.recordingDir = root.optString("recordingDir", "Music/时屿");
        d.alarmsSeeded = root.optBoolean("alarmsSeeded", false);
        JSONObject v = root.optJSONObject("vault");
        if (v != null) d.vault = parseVault(v);
        return d;
    }

    private static JSONArray holidaysToJson(List<Holiday> holidays) throws JSONException {
        JSONArray arr = new JSONArray();
        for (Holiday h : holidays) {
            JSONObject o = new JSONObject();
            o.put("date", h.date);
            o.put("name", h.name);
            o.put("type", h.workday ? "workday" : "holiday");
            arr.put(o);
        }
        return arr;
    }

    private static ArrayList<Holiday> parseHolidays(JSONArray arr) {
        ArrayList<Holiday> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String date = o.optString("date", "").trim();
            if (Dates.parseDate(date) == null) continue;
            Holiday h = new Holiday();
            h.date = date;
            h.name = o.optString("name", "").trim();
            String type = o.optString("type", "holiday");
            h.workday = type.equals("workday") || type.equals("调休") || type.equals("上班");
            list.add(h);
        }
        return list;
    }

    private static JSONArray alarmsToJson(List<Alarm> alarms) throws JSONException {
        JSONArray arr = new JSONArray();
        for (Alarm a : alarms) {
            JSONObject o = new JSONObject();
            o.put("id", a.id);
            o.put("label", a.label);
            o.put("hour", a.hour);
            o.put("minute", a.minute);
            o.put("enabled", a.enabled);
            o.put("mode", a.mode);
            o.put("weekdays", a.weekdays);
            o.put("startDate", a.startDate);
            o.put("endDate", a.endDate);
            JSONArray extra = new JSONArray();
            for (String d : a.extraDates) extra.put(d);
            o.put("extraDates", extra);
            o.put("repeatCount", a.repeatCount);
            o.put("repeatIntervalMin", a.repeatIntervalMin);
            o.put("ringSeconds", a.ringSeconds);
            o.put("vibrate", a.vibrate);
            o.put("createdAt", a.createdAt);
            arr.put(o);
        }
        return arr;
    }

    private static ArrayList<Alarm> parseAlarms(JSONArray arr) {
        ArrayList<Alarm> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Alarm a = new Alarm();
            a.id = o.optString("id", newId("alarm"));
            a.label = o.optString("label", "闹钟");
            a.hour = Math.max(0, Math.min(23, o.optInt("hour", 7)));
            a.minute = Math.max(0, Math.min(59, o.optInt("minute", 30)));
            a.enabled = o.optBoolean("enabled", true);
            a.mode = o.optString("mode", "workday");
            a.weekdays = o.optInt("weekdays", 0x1F);
            a.startDate = o.optString("startDate", "");
            a.endDate = o.optString("endDate", "");
            a.repeatCount = Math.max(1, Math.min(10, o.optInt("repeatCount", 1)));
            a.repeatIntervalMin = Math.max(1, Math.min(60, o.optInt("repeatIntervalMin", 5)));
            a.ringSeconds = Math.max(5, Math.min(120, o.optInt("ringSeconds", 30)));
            a.vibrate = o.optBoolean("vibrate", false);
            a.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            JSONArray extra = o.optJSONArray("extraDates");
            if (extra != null) {
                for (int j = 0; j < extra.length(); j++) {
                    String d = extra.optString(j, "").trim();
                    if (Dates.parseDate(d) != null) a.extraDates.add(d);
                }
            }
            list.add(a);
        }
        return list;
    }

    private static JSONArray recordingsToJson(List<Recording> recordings) throws JSONException {
        JSONArray arr = new JSONArray();
        if (recordings == null) return arr;
        for (Recording r : recordings) {
            JSONObject o = new JSONObject();
            o.put("id", r.id);
            o.put("name", r.name);
            o.put("uri", r.uri);
            o.put("durationMs", r.durationMs);
            o.put("size", r.size);
            o.put("createdAt", r.createdAt);
            arr.put(o);
        }
        return arr;
    }

    private static ArrayList<Recording> parseRecordings(JSONArray arr) {
        ArrayList<Recording> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Recording r = new Recording();
            r.id = o.optString("id", newId("rec"));
            r.name = o.optString("name", "录音");
            r.uri = o.optString("uri", "");
            r.durationMs = o.optLong("durationMs", 0);
            r.size = o.optLong("size", 0);
            r.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            list.add(r);
        }
        return list;
    }

    public static JSONObject timetableToJson(Timetable t) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema", TIMETABLE_SCHEMA);
        root.put("version", 1);
        root.put("name", safe(t.name));
        root.put("termStartDate", safe(t.termStart));
        JSONArray slots = new JSONArray();
        for (TimeSlot s : t.timeSlots) {
            JSONObject o = new JSONObject();
            o.put("id", safe(s.id));
            o.put("name", safe(s.name));
            JSONArray segs = new JSONArray();
            for (Segment seg : s.segments) {
                JSONObject so = new JSONObject();
                so.put("start", safe(seg.start));
                so.put("end", safe(seg.end));
                segs.put(so);
            }
            o.put("segments", segs);
            slots.put(o);
        }
        root.put("timeSlots", slots);
        JSONArray courses = new JSONArray();
        for (Course c : t.courses) {
            JSONObject o = new JSONObject();
            o.put("id", safe(c.id));
            o.put("name", safe(c.name));
            o.put("code", safe(c.code));
            o.put("location", safe(c.location));
            JSONArray teachers = new JSONArray();
            for (String teacher : c.teachers) teachers.put(teacher);
            o.put("teachers", teachers);
            o.put("color", colorHex(c.color));
            JSONArray sessions = new JSONArray();
            for (Session s : c.sessions) {
                ArrayList<WeekRange> weeks = s.effectiveWeeks();
                JSONObject so = new JSONObject();
                so.put("dayOfWeek", s.day);
                so.put("slotId", safe(s.slotId));
                if (s.endSlotId != null && !s.endSlotId.isEmpty()) {
                    so.put("endSlotId", s.endSlotId);
                }
                so.put("weeks", WeekSpec.format(weeks));
                JSONArray ranges = new JSONArray();
                for (WeekRange r : weeks) {
                    JSONObject ro = new JSONObject();
                    ro.put("start", r.start);
                    ro.put("end", r.end);
                    ro.put("parity", r.parity);
                    ranges.put(ro);
                }
                so.put("weekRanges", ranges);
                WeekRange first = weeks.get(0);
                WeekRange last = weeks.get(weeks.size() - 1);
                so.put("startWeek", first.start);
                so.put("endWeek", last.end);
                so.put("weekParity", weeks.size() == 1 ? first.parity : "all");
                if (s.location != null && !s.location.isEmpty()) so.put("location", s.location);
                if (s.teachers != null && !s.teachers.isEmpty()) {
                    JSONArray st = new JSONArray();
                    for (String teacher : s.teachers) st.put(teacher);
                    so.put("teachers", st);
                }
                sessions.put(so);
            }
            o.put("sessions", sessions);
            courses.put(o);
        }
        root.put("courses", courses);
        return root;
    }

    public String timetableJson() {
        try {
            return timetableToJson(data.timetable).toString(2);
        } catch (JSONException e) {
            return "{}";
        }
    }

    // ------------------------------------------------------------ timetable

    public static Timetable parseTimetableText(String text) throws JSONException {
        return parseTimetable(new JSONObject(text));
    }

    private static Timetable parseTimetable(JSONObject root) throws JSONException {
        Timetable t = new Timetable();
        t.name = root.optString("name", root.optString("title", "我的课表")).trim();
        if (t.name.isEmpty()) t.name = "我的课表";
        String term = root.optString("termStartDate",
                root.optString("termStart", root.optString("startDate", "")));
        LocalDate start = Dates.parseDate(term);
        if (start == null) {
            throw new IllegalArgumentException("缺少有效的 termStartDate（格式 yyyy-MM-dd）");
        }
        t.termStart = Dates.date(Dates.mondayOf(start));

        JSONArray slotArray = root.optJSONArray("timeSlots");
        if (slotArray == null) slotArray = root.optJSONArray("periods");
        if (slotArray == null) slotArray = new JSONArray();
        if (slotArray.length() > 60) throw new IllegalArgumentException("时间段过多（上限 60）");
        Set<String> slotIds = new HashSet<>();
        for (int i = 0; i < slotArray.length(); i++) {
            JSONObject o = slotArray.optJSONObject(i);
            if (o == null) continue;
            TimeSlot s = new TimeSlot();
            s.id = o.optString("id", "").trim();
            if (s.id.isEmpty() || slotIds.contains(s.id)) s.id = newId("slot");
            slotIds.add(s.id);
            s.name = o.optString("name", o.optString("title", "第 " + (i + 1) + " 节")).trim();
            if (s.name.isEmpty()) s.name = "第 " + (i + 1) + " 节";

            JSONArray segments = o.optJSONArray("segments");
            if (segments != null) {
                for (int j = 0; j < segments.length(); j++) {
                    JSONObject so = segments.optJSONObject(j);
                    if (so == null) continue;
                    addSegment(s, so.optString("start", ""), so.optString("end", ""), t.name);
                }
            }
            if (s.segments.isEmpty()) {
                addSegment(s, o.optString("start", ""), o.optString("end", ""), t.name);
            }
            if (s.segments.isEmpty()) {
                throw new IllegalArgumentException("时间段「" + s.name + "」缺少 start / end");
            }
            s.segments.sort(Comparator.comparingInt(a -> Dates.minutes(a.start)));
            t.timeSlots.add(s);
        }

        JSONArray courseArray = root.optJSONArray("courses");
        if (courseArray == null) courseArray = new JSONArray();
        if (courseArray.length() > 300) throw new IllegalArgumentException("课程过多（上限 300）");
        for (int i = 0; i < courseArray.length(); i++) {
            JSONObject o = courseArray.optJSONObject(i);
            if (o == null) continue;
            Course c = new Course();
            c.id = o.optString("id", "").trim();
            if (c.id.isEmpty()) c.id = newId("course");
            c.name = o.optString("name", o.optString("title", "")).trim();
            if (c.name.isEmpty()) throw new IllegalArgumentException("第 " + (i + 1) + " 门课程缺少 name");
            c.code = o.optString("code", o.optString("courseCode", "")).trim();
            c.location = o.optString("location", o.optString("place", "")).trim();
            Object teacherObj = o.opt("teachers");
            if (teacherObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) teacherObj;
                for (int j = 0; j < arr.length(); j++) {
                    String s = arr.optString(j, "").trim();
                    if (!s.isEmpty()) c.teachers.add(s);
                }
            } else if (teacherObj != null) {
                for (String s : String.valueOf(teacherObj).split("[、,，;；/]")) {
                    if (!s.trim().isEmpty()) c.teachers.add(s.trim());
                }
            }
            if (c.teachers.isEmpty()) {
                String one = o.optString("teacher", "").trim();
                if (!one.isEmpty()) c.teachers.add(one);
            }
            c.color = parseColor(o.opt("color"), Palette.COURSE_COLORS[i % Palette.COURSE_COLORS.length]);

            JSONArray sessions = o.optJSONArray("sessions");
            if (sessions == null) sessions = o.optJSONArray("times");
            if (sessions != null) {
                for (int j = 0; j < sessions.length(); j++) {
                    JSONObject so = sessions.optJSONObject(j);
                    if (so == null) continue;
                    Session session = new Session();
                    session.day = so.optInt("dayOfWeek", so.optInt("day", 1));
                    if (session.day < 1 || session.day > 7) {
                        throw new IllegalArgumentException("课程「" + c.name + "」的 dayOfWeek 必须在 1–7");
                    }
                    session.slotId = so.optString("slotId", so.optString("periodId", "")).trim();
                    if (session.slotId.isEmpty() || !slotIds.contains(session.slotId)) {
                        throw new IllegalArgumentException("课程「" + c.name + "」引用了不存在的时间段：" + session.slotId);
                    }
                    String endSlot = so.optString("endSlotId", "").trim();
                    session.endSlotId = !endSlot.isEmpty() && slotIds.contains(endSlot) ? endSlot : "";
                    String weeksText = so.optString("weeks", "").trim();
                    JSONArray ranges = so.optJSONArray("weekRanges");
                    if (ranges != null && ranges.length() > 0) {
                        for (int k = 0; k < ranges.length(); k++) {
                            JSONObject ro = ranges.optJSONObject(k);
                            if (ro == null) continue;
                            WeekRange r = new WeekRange();
                            r.start = Math.max(1, ro.optInt("start", 1));
                            r.end = Math.max(r.start, ro.optInt("end", r.start));
                            r.parity = normalizeParity(ro.optString("parity", "all"));
                            session.weeks.add(r);
                        }
                    } else if (!weeksText.isEmpty()) {
                        session.weeks = WeekSpec.parse(weeksText);
                    } else {
                        session.startWeek = so.optInt("startWeek", 1);
                        session.endWeek = so.optInt("endWeek", 20);
                        if (session.startWeek < 1) session.startWeek = 1;
                        if (session.endWeek < session.startWeek) session.endWeek = session.startWeek;
                        String parity = so.optString("weekParity", so.optString("parity", "all"));
                        session.weeks = WeekSpec.one(session.startWeek, session.endWeek, parity);
                    }
                    if (session.weeks.isEmpty()) {
                        throw new IllegalArgumentException("课程「" + c.name + "」有一段安排没有有效周次");
                    }
                    session.startWeek = session.weeks.get(0).start;
                    session.endWeek = session.weeks.get(session.weeks.size() - 1).end;
                    session.parity = session.weeks.size() == 1 ? session.weeks.get(0).parity : "all";

                    session.location = so.optString("location", "").trim();
                    Object stObj = so.opt("teachers");
                    if (stObj instanceof JSONArray) {
                        JSONArray st = (JSONArray) stObj;
                        for (int k = 0; k < st.length(); k++) {
                            String teacher = st.optString(k, "").trim();
                            if (!teacher.isEmpty()) session.teachers.add(teacher);
                        }
                    } else if (stObj instanceof String) {
                        for (String teacher : ((String) stObj).split("[、,，;；/]")) {
                            if (!teacher.trim().isEmpty()) session.teachers.add(teacher.trim());
                        }
                    }
                    c.sessions.add(session);
                }
            }
            t.courses.add(c);
        }
        return t;
    }

    private static void addSegment(TimeSlot s, String start, String end, String tableName) {
        if (start == null || end == null || start.trim().isEmpty() || end.trim().isEmpty()) return;
        int a = Dates.minutes(start);
        int b = Dates.minutes(end);
        if (a < 0 || b < 0) throw new IllegalArgumentException("时间段「" + s.name + "」的时间格式应为 HH:mm");
        if (b <= a) throw new IllegalArgumentException("时间段「" + s.name + "」的结束时间必须晚于开始时间");
        s.segments.add(new Segment(Dates.time(a), Dates.time(b)));
    }

    public boolean isHoliday(LocalDate date) {
        String key = Dates.date(date);
        for (Holiday h : data.holidays) {
            if (h.date.equals(key)) return !h.workday;
        }
        return DefaultHolidays.isHoliday(date);
    }

    public boolean isMakeupWorkday(LocalDate date) {
        String key = Dates.date(date);
        for (Holiday h : data.holidays) {
            if (h.date.equals(key) && h.workday) return true;
        }
        return false;
    }

    /** Weekends are non-workdays unless explicitly marked as a make-up workday. */
    public boolean isWorkday(LocalDate date) {
        if (isMakeupWorkday(date)) return true;
        if (isHoliday(date)) return false;
        int dow = Dates.dow(date);
        return dow <= 5;
    }

    public String holidayName(LocalDate date) {
        String key = Dates.date(date);
        for (Holiday h : data.holidays) {
            if (h.date.equals(key)) return h.name;
        }
        return DefaultHolidays.nameOf(date);
    }

    public static String normalizeParity(String parity) {
        if (parity == null) return "all";
        String p = parity.trim().toLowerCase(Locale.ROOT);
        if (p.equals("odd") || p.equals("单周") || p.equals("单")) return "odd";
        if (p.equals("even") || p.equals("双周") || p.equals("双")) return "even";
        return "all";
    }

    public static String parityLabel(String parity) {
        if ("odd".equals(parity)) return "单周";
        if ("even".equals(parity)) return "双周";
        return "每周";
    }

    public void replaceTimetable(Timetable t) {
        data.timetable = t;
        save();
    }

    /** The curated 2026–2027 first-semester table, also used on first run. */
    public Timetable builtInSemesterTimetable() {
        return demoMode ? demoSeedData().timetable : seedData().timetable;
    }

    public static ArrayList<Holiday> parseHolidayText(String text) throws JSONException {
        JSONObject root = new JSONObject(text);
        JSONArray arr = root.optJSONArray("holidays");
        if (arr == null) arr = root.optJSONArray("days");
        if (arr == null) throw new IllegalArgumentException("缺少 holidays 数组");
        ArrayList<Holiday> list = parseHolidays(arr);
        if (list.isEmpty()) throw new IllegalArgumentException("没有有效的节假日日期");
        return list;
    }

    public static boolean occursOn(Course c, Session s, LocalDate date, LocalDate termStart) {
        if (Dates.dow(date) != s.day) return false;
        int week = Dates.weekOf(termStart, date);
        return WeekSpec.covers(s.effectiveWeeks(), week);
    }

    public static List<Course> coursesOn(Timetable table, LocalDate date) {
        LocalDate term = Dates.parseDate(table.termStart);
        if (term == null) return Collections.emptyList();
        ArrayList<Course> result = new ArrayList<>();
        for (Course c : table.courses) {
            for (Session s : c.sessions) {
                if (occursOn(c, s, date, term)) {
                    result.add(c);
                    break;
                }
            }
        }
        return result;
    }

    public static TimeSlot slotById(Timetable table, String id) {
        for (TimeSlot s : table.timeSlots) if (s.id.equals(id)) return s;
        return null;
    }

    public static int slotIndex(Timetable table, String id) {
        if (table == null) return -1;
        for (int i = 0; i < table.timeSlots.size(); i++) {
            if (table.timeSlots.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    public static int sessionEndIndex(Timetable table, Session session) {
        int start = slotIndex(table, session.slotId);
        if (start < 0) return -1;
        int end = session.endSlotId == null || session.endSlotId.isEmpty()
                ? start : slotIndex(table, session.endSlotId);
        return Math.max(start, end);
    }

    public static String sessionSlotLabel(Timetable table, Session session) {
        int a = slotIndex(table, session.slotId);
        int b = sessionEndIndex(table, session);
        if (a < 0) return "未知节次";
        if (b == a) return table.timeSlots.get(a).name;
        return "第 " + (a + 1) + "–" + (b + 1) + " 节";
    }

    public static Session sessionOn(Course c, LocalDate date, LocalDate term) {
        for (Session s : c.sessions) if (occursOn(c, s, date, term)) return s;
        return null;
    }

    public List<Todo> todosOn(LocalDate date) {
        ArrayList<Todo> result = new ArrayList<>();
        for (Todo t : data.todos) {
            if (t.dueAt > 0 && Dates.dateOf(t.dueAt).equals(date)) result.add(t);
        }
        result.sort(Comparator.comparing((Todo t) -> t.done)
                .thenComparingLong(t -> t.dueAt <= 0 ? Long.MAX_VALUE : t.dueAt));
        return result;
    }

    // ------------------------------------------------------------- notes io

    public String notesJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", NOTES_SCHEMA);
            root.put("version", 1);
            root.put("exportedAt", Dates.date(Dates.today()) + " " + Dates.nowTime());
            root.put("notes", notesToJson(data.notes));
            root.put("todos", todosToJson(data.todos));
            if (data.vault != null) root.put("private", vaultToJson(data.vault));
            return root.toString(2);
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static NotesBundle parseNotesText(String text) throws JSONException {
        JSONObject root = new JSONObject(text);
        NotesBundle b = new NotesBundle();
        b.exportedAt = root.optString("exportedAt", "");
        b.notes.addAll(parseNotes(root.optJSONArray("notes")));
        b.todos.addAll(parseTodos(root.optJSONArray("todos")));
        JSONObject p = root.optJSONObject("private");
        if (p == null) p = root.optJSONObject("privateVault");
        if (p != null) b.vault = parseVault(p);
        if (b.notes.isEmpty() && b.todos.isEmpty() && b.vault == null) {
            throw new IllegalArgumentException("文件里没有可导入的笔记或待办");
        }
        return b;
    }

    public ImportResult applyNotesBundle(NotesBundle b, boolean replace) {
        ImportResult r = new ImportResult();
        if (replace) {
            data.notes.clear();
            data.todos.clear();
        }
        Set<String> noteIds = new HashSet<>();
        for (Note n : data.notes) noteIds.add(n.id);
        for (Note n : b.notes) {
            if (n.id == null || n.id.isEmpty() || noteIds.contains(n.id)) n.id = newId("note");
            noteIds.add(n.id);
            data.notes.add(n);
            r.notes++;
        }
        Set<String> todoIds = new HashSet<>();
        for (Todo t : data.todos) todoIds.add(t.id);
        for (Todo t : b.todos) {
            if (t.id == null || t.id.isEmpty() || todoIds.contains(t.id)) t.id = newId("todo");
            todoIds.add(t.id);
            data.todos.add(t);
            r.todos++;
        }
        if (b.vault != null) {
            if (data.vault == null) {
                data.vault = b.vault;
                r.privateImported = true;
                r.privateKeptLocked = true;
            } else if (sameVault(data.vault, b.vault)) {
                Set<String> ids = new HashSet<>();
                for (PrivateNote n : data.vault.notes) ids.add(n.id);
                for (PrivateNote n : b.vault.notes) {
                    if (!ids.contains(n.id)) data.vault.notes.add(n);
                }
                r.privateImported = true;
            } else {
                r.privateSkipped = true;
            }
        }
        save();
        return r;
    }

    private static boolean sameVault(Vault a, Vault b) {
        return a != null && b != null && safe(a.salt).equals(safe(b.salt))
                && safe(a.verifierCipher).equals(safe(b.verifierCipher));
    }

    private static JSONArray notesToJson(List<Note> notes) throws JSONException {
        JSONArray arr = new JSONArray();
        for (Note n : notes) {
            JSONObject o = new JSONObject();
            o.put("id", safe(n.id));
            o.put("title", safe(n.title));
            o.put("body", safe(n.body));
            o.put("createdAt", n.createdAt);
            o.put("updatedAt", n.updatedAt);
            o.put("pinned", n.pinned);
            o.put("color", colorHex(n.color));
            o.put("attachments", attachmentsToJson(n.attachments));
            arr.put(o);
        }
        return arr;
    }

    private static ArrayList<Note> parseNotes(JSONArray arr) {
        ArrayList<Note> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Note n = new Note();
            n.id = o.optString("id", "");
            if (n.id.isEmpty()) n.id = newId("note");
            n.title = o.optString("title", "无标题");
            n.body = o.optString("body", o.optString("content", ""));
            n.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            n.updatedAt = o.optLong("updatedAt", n.createdAt);
            n.pinned = o.optBoolean("pinned", false);
            n.color = parseColor(o.opt("color"), Palette.NOTE_COLORS[i % Palette.NOTE_COLORS.length]);
            n.attachments = parseAttachments(o.optJSONArray("attachments"));
            list.add(n);
        }
        return list;
    }

    private static JSONArray todosToJson(List<Todo> todos) throws JSONException {
        JSONArray arr = new JSONArray();
        for (Todo t : todos) {
            JSONObject o = new JSONObject();
            o.put("id", safe(t.id));
            o.put("title", safe(t.title));
            o.put("note", safe(t.note));
            o.put("remindAt", t.remindAt);
            o.put("dueAt", t.dueAt);
            o.put("done", t.done);
            o.put("createdAt", t.createdAt);
            o.put("doneAt", t.doneAt);
            o.put("priority", t.priority);
            o.put("advanceMinutes", t.advanceMinutes);
            o.put("advanceAlarm", t.advanceAlarm);
            o.put("attachments", attachmentsToJson(t.attachments));
            arr.put(o);
        }
        return arr;
    }

    private static ArrayList<Todo> parseTodos(JSONArray arr) {
        ArrayList<Todo> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Todo t = new Todo();
            t.id = o.optString("id", "");
            if (t.id.isEmpty()) t.id = newId("todo");
            t.title = o.optString("title", "");
            if (t.title.trim().isEmpty()) continue;
            t.note = o.optString("note", o.optString("body", ""));
            long due = o.optLong("dueAt", 0);
            if (due == 0) {
                String date = o.optString("dueDate", "");
                String time = o.optString("dueTime", "");
                LocalDate d = Dates.parseDate(date);
                if (d != null) {
                    int m = Dates.minutes(time);
                    due = Dates.at(d, m < 0 ? 9 : m / 60, m < 0 ? 0 : m % 60);
                }
            }
            long remind = o.optLong("remindAt", 0);
            if (remind == 0) {
                String date = o.optString("remindDate", "");
                String time = o.optString("remindTime", "");
                LocalDate d = Dates.parseDate(date);
                if (d != null) {
                    int m = Dates.minutes(time);
                    remind = Dates.at(d, m < 0 ? 9 : m / 60, m < 0 ? 0 : m % 60);
                }
            }
            t.remindAt = remind;
            t.dueAt = due;
            t.done = o.optBoolean("done", false);
            t.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            t.doneAt = o.optLong("doneAt", 0);
            t.priority = o.optInt("priority", 0);
            t.advanceMinutes = o.optInt("advanceMinutes", 120);
            t.advanceAlarm = o.optBoolean("advanceAlarm", false);
            t.attachments = parseAttachments(o.optJSONArray("attachments"));
            list.add(t);
        }
        return list;
    }

    public static JSONArray attachmentsToJson(List<Attachment> attachments) throws JSONException {
        JSONArray arr = new JSONArray();
        if (attachments == null) return arr;
        for (Attachment a : attachments) {
            JSONObject o = new JSONObject();
            o.put("id", safe(a.id));
            o.put("type", safe(a.type));
            o.put("name", safe(a.name));
            o.put("path", safe(a.path));
            o.put("uri", safe(a.uri));
            o.put("mime", safe(a.mime));
            o.put("size", a.size);
            o.put("createdAt", a.createdAt);
            arr.put(o);
        }
        return arr;
    }

    public static ArrayList<Attachment> parseAttachments(JSONArray arr) {
        ArrayList<Attachment> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Attachment a = new Attachment();
            a.id = o.optString("id", newId("att"));
            a.type = o.optString("type", "file");
            a.name = o.optString("name", "附件");
            a.path = o.optString("path", "");
            a.uri = o.optString("uri", "");
            a.mime = o.optString("mime", "");
            a.size = o.optLong("size", 0);
            a.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            if (!a.path.isEmpty() || !a.uri.isEmpty()) list.add(a);
        }
        return list;
    }

    private static JSONObject vaultToJson(Vault v) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("salt", safe(v.salt));
        o.put("verifierIv", safe(v.verifierIv));
        o.put("verifierCipher", safe(v.verifierCipher));
        JSONArray arr = new JSONArray();
        for (PrivateNote n : v.notes) {
            JSONObject no = new JSONObject();
            no.put("id", safe(n.id));
            no.put("iv", safe(n.iv));
            no.put("cipher", safe(n.cipher));
            no.put("updatedAt", n.updatedAt);
            arr.put(no);
        }
        o.put("notes", arr);
        o.put("cipher", "AES-256-GCM");
        o.put("kdf", "PBKDF2-HMAC-SHA256");
        o.put("note", "内容仅为密文；导入后需使用原密码解锁。");
        return o;
    }

    private static Vault parseVault(JSONObject o) {
        if (o == null) return null;
        String salt = o.optString("salt", "");
        String iv = o.optString("verifierIv", "");
        String cipher = o.optString("verifierCipher", "");
        if (salt.isEmpty() || iv.isEmpty() || cipher.isEmpty()) return null;
        Vault v = new Vault();
        v.salt = salt;
        v.verifierIv = iv;
        v.verifierCipher = cipher;
        JSONArray arr = o.optJSONArray("notes");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject no = arr.optJSONObject(i);
                if (no == null) continue;
                PrivateNote n = new PrivateNote();
                n.id = no.optString("id", newId("secret"));
                n.iv = no.optString("iv", "");
                n.cipher = no.optString("cipher", "");
                n.updatedAt = no.optLong("updatedAt", System.currentTimeMillis());
                if (!n.iv.isEmpty() && !n.cipher.isEmpty()) v.notes.add(n);
            }
        }
        return v;
    }

    // --------------------------------------------------------------- helpers

    public static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String colorHex(int color) {
        return String.format(Locale.ROOT, "#%06X", 0xFFFFFF & color);
    }

    public static int parseColor(Object value, int fallback) {
        try {
            if (value instanceof Number) return ((Number) value).intValue();
            if (value != null) {
                String s = String.valueOf(value).trim();
                if (!s.isEmpty()) {
                    if (!s.startsWith("#")) s = "#" + s;
                    return Color.parseColor(s);
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public String exportName(String prefix) {
        return prefix + "-" + Dates.stamp() + ".json";
    }

    public String appJsonForDebug() {
        try {
            return appToJson(data).toString(2);
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static String base64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    public static byte[] unbase64(String value) {
        return Base64.decode(value, Base64.NO_WRAP);
    }

    public static LocalDate dateOfEpoch(long millis) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault()).toLocalDate();
    }

    public static LocalTime timeOfEpoch(long millis) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault()).toLocalTime();
    }
}
