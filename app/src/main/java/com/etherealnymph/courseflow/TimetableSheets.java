package com.etherealnymph.courseflow;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;

/** Course and time-slot editors shared by the timetable and settings screens. */
public final class TimetableSheets {

    private TimetableSheets() {}

    // ---------------------------------------------------------------- term

    public static void editTerm(MainActivity a) {
        Palette p = a.palette();
        Store store = a.store();
        Store.Timetable table = store.data.timetable;
        LocalDate start = Dates.parseDate(table.termStart);
        if (start == null) start = Dates.mondayOf(Dates.today());
        final LocalDate[] picked = {Dates.mondayOf(start)};

        Sheet sheet = new Sheet(a, p, "学期与第一周", "第一周从所选日期所在的周一开始计算");
        EditText name = Ui.input(a, "学期名称，例如：2026 秋季学期", p, false);
        name.setText(table.name);
        sheet.label("学期名称");
        sheet.add(name);

        sheet.label("第一周开始日");
        TextView date = Ui.secondaryButton(a, Dates.date(picked[0]) + "  ·  " + Dates.weekFull(Dates.dow(picked[0])), p);
        date.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        date.setOnClickListener(v -> Pickers.date(a, p, picked[0], d -> {
            picked[0] = Dates.mondayOf(d);
            date.setText(Dates.date(picked[0]) + "  ·  周一");
        }));
        sheet.add(date);
        TextView hint = Ui.text(a, "课表按周一至周日为一周计算；修改后每周的周次会重新对齐。", 11.5f, p.faint, false);
        hint.setPadding(0, Ui.dp(a, 8), 0, 0);
        sheet.add(hint);

        TextView save = Ui.primaryButton(a, "保存学期设置", p);
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) n = "我的课表";
            table.name = n;
            table.termStart = Dates.date(picked[0]);
            store.save();
            sheet.dismiss();
            a.scheduleWeekOffset = 0;
            a.agendaWeekOffset = 0;
            a.refresh();
            Ui.toast(a, "学期设置已更新");
        });
        sheet.footer().addView(save);
        sheet.show();
    }

    // -------------------------------------------------------------- detail

    public static void courseDetail(MainActivity a, Store.Course course, LocalDate date) {
        Palette p = a.palette();
        Store store = a.store();
        Sheet sheet = new Sheet(a, p, course.name, null);

        LinearLayout hero = new LinearLayout(a);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16));
        hero.setBackground(Ui.bg(a, Palette.blend(course.color, p.surface, p.dark ? 0.30f : 0.17f),
                Ui.dp(a, 18)));
        TextView when = Ui.text(a, date == null ? "课程安排" : Dates.monthDay(date) + " · " + Dates.weekFull(Dates.dow(date)),
                11.5f, Palette.blend(course.color, p.ink, p.dark ? 0.35f : 0.55f), true);
        when.setLetterSpacing(0.06f);
        hero.addView(when);
        TextView name = Ui.text(a, course.name, 22, p.ink, true);
        name.setPadding(0, Ui.dp(a, 5), 0, 0);
        hero.addView(name);
        LinearLayout meta = Ui.row(a);
        meta.setPadding(0, Ui.dp(a, 8), 0, 0);
        if (course.code != null && !course.code.isEmpty()) {
            TextView code = Ui.pill(a, course.code, course.color, p);
            meta.addView(code);
            meta.addView(Ui.hspacer(a, 6));
        }
        Store.Session firstSession = course.sessions.isEmpty() ? null : course.sessions.get(0);
        String locText = Store.effectiveLocation(course, firstSession);
        if (!locText.isEmpty()) {
            meta.addView(Ui.icon(a, R.drawable.ic_location, 14, p.sub));
            TextView loc = Ui.text(a, locText, 11.5f, p.sub, false);
            loc.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 10), 0);
            meta.addView(loc);
        }
        String teacherText = Store.teacherLine(course, firstSession);
        if (!teacherText.isEmpty()) {
            meta.addView(Ui.icon(a, R.drawable.ic_person, 14, p.sub));
            TextView teacher = Ui.text(a, teacherText, 11.5f, p.sub, false);
            teacher.setPadding(Ui.dp(a, 4), 0, 0, 0);
            meta.addView(teacher);
        }
        hero.addView(meta);
        sheet.add(hero);

        sheet.label("上课时间");
        for (Store.Session s : course.sessions) {
            LinearLayout row = Ui.row(a);
            row.setBackground(Ui.bg(a, p.surfaceAlt, Ui.dp(a, 14)));
            row.setPadding(Ui.dp(a, 12), Ui.dp(a, 9), Ui.dp(a, 12), Ui.dp(a, 9));
            TextView day = Ui.text(a, Dates.weekShort(s.day), 12, p.primary, true);
            day.setGravity(Gravity.CENTER);
            day.setBackground(Ui.bg(a, p.primarySoft, Ui.dp(a, 10)));
            day.setMinWidth(Ui.dp(a, 30));
            day.setMinHeight(Ui.dp(a, 30));
            row.addView(day);
            LinearLayout lines = Ui.column(a);
            lines.setPadding(Ui.dp(a, 10), 0, 0, 0);
            Store.TimeSlot start = Store.slotById(store.data.timetable, s.slotId);
            int endIdx = Store.sessionEndIndex(store.data.timetable, s);
            Store.TimeSlot end = endIdx >= 0 ? store.data.timetable.timeSlots.get(endIdx) : null;
            String time = start == null ? "未知节次" : start.span();
            if (end != null && end != start) time += " – " + end.span();
            TextView first = Ui.text(a, Store.sessionSlotLabel(store.data.timetable, s)
                    + "  ·  " + time, 12.5f, p.ink, true);
            lines.addView(first);
            TextView second = Ui.text(a, weekLine(s), 10.5f, p.faint, false);
            second.setPadding(0, Ui.dp(a, 3), 0, 0);
            lines.addView(second);
            String sLoc = Store.effectiveLocation(course, s);
            String sTeacher = Store.teacherLine(course, s);
            if (!sLoc.isEmpty() || !sTeacher.isEmpty()) {
                TextView third = Ui.text(a, (sLoc.isEmpty() ? "" : sLoc)
                        + (sLoc.isEmpty() || sTeacher.isEmpty() ? "" : "  ·  ") + sTeacher,
                        10, p.sub, false);
                third.setPadding(0, Ui.dp(a, 3), 0, 0);
                lines.addView(third);
            }
            row.addView(lines, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(a, 6);
            sheet.add(row, lp);
        }

        TextView edit = Ui.primaryButton(a, "编辑课程", p);
        edit.setOnClickListener(v -> {
            sheet.dismiss();
            editCourse(a, course);
        });
        TextView delete = Ui.dangerButton(a, "删除", p);
        delete.setOnClickListener(v -> Sheet.confirm(a, p, "删除这门课程？",
                "「" + course.name + "」的所有上课安排都会被删除，此操作无法撤销。", "删除", true, () -> {
                    store.data.timetable.courses.remove(course);
                    store.save();
                    sheet.dismiss();
                    a.refresh();
                    Ui.toast(a, "课程已删除");
                }));
        sheet.actions(delete, edit);
        sheet.show();
    }

    public static String weekLine(Store.Session s) {
        return WeekSpec.describe(s.effectiveWeeks());
    }

    // --------------------------------------------------------------- editor

    public static void editCourse(MainActivity a, Store.Course existing) {
        Palette p = a.palette();
        Store store = a.store();
        Store.Timetable table = store.data.timetable;
        if (table.timeSlots.isEmpty()) {
            Sheet.confirm(a, p, "还没有时间段", "先创建至少一个上课时间段，才能安排课程。",
                    "去添加时间段", false, () -> slotManager(a));
            return;
        }
        boolean isNew = existing == null;
        Store.Course draft = isNew ? new Store.Course() : copyCourse(existing);
        if (isNew) {
            draft.id = Store.newId("course");
            draft.color = Palette.COURSE_COLORS[table.courses.size() % Palette.COURSE_COLORS.length];
        }

        Sheet sheet = new Sheet(a, p, isNew ? "添加课程" : "编辑课程",
                isNew ? "课程名称、地点、教师与上课时间" : "修改会立即应用到课程表");
        EditText name = Ui.input(a, "课程名称，例如：电磁场理论", p, false);
        name.setText(draft.name);
        sheet.label("课程名称");
        sheet.add(name);
        EditText code = Ui.input(a, "课程代码，例如：G2700940.03", p, false);
        code.setText(draft.code);
        sheet.label("课程代码");
        sheet.add(code);
        EditText location = Ui.input(a, "默认地点（单个时段可单独覆盖）", p, false);
        location.setText(draft.location);
        sheet.label("默认地点");
        sheet.add(location);
        EditText teachers = Ui.input(a, "多位教师用顿号或逗号分隔", p, false);
        teachers.setText(String.join("、", draft.teachers));
        sheet.label("默认教师");
        sheet.add(teachers);

        sheet.label("课程颜色");
        LinearLayout swatchWrap = Ui.row(a);
        sheet.add(swatchWrap);
        renderSwatches(a, p, swatchWrap, draft);

        LinearLayout sessionHead = Ui.row(a);
        TextView label = Ui.section(a, "上课时间", p);
        sessionHead.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView addSession = Ui.textButton(a, "＋ 添加", p.primary, p);
        sessionHead.addView(addSession);
        LinearLayout.LayoutParams shlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shlp.topMargin = Ui.dp(a, 16);
        sheet.add(sessionHead, shlp);

        LinearLayout sessionList = Ui.column(a);
        sheet.add(sessionList);
        Runnable render = () -> {
            renderSessions(a, p, table, sessionList, draft);
            if (draft.sessions.isEmpty()) {
                TextView hint = Ui.text(a, "还没有安排。点「添加」选择星期与时间段。", 12, p.faint, false);
                hint.setPadding(0, Ui.dp(a, 8), 0, 0);
                sessionList.addView(hint);
            }
        };
        render.run();
        addSession.setOnClickListener(v -> openSessionPicker(a, p, table, null, session -> {
            for (Store.Session s : draft.sessions) {
                if (sessionsOverlap(table, s, session)) {
                    Ui.toast(a, "这个时间已经有安排了");
                    return;
                }
            }
            draft.sessions.add(session);
            render.run();
        }));

        TextView save = Ui.primaryButton(a, isNew ? "添加到课程表" : "保存修改", p);
        save.setOnClickListener(v -> {
            String courseName = name.getText().toString().trim();
            if (courseName.isEmpty()) {
                Ui.toast(a, "请填写课程名称");
                name.requestFocus();
                return;
            }
            if (draft.sessions.isEmpty()) {
                Ui.toast(a, "请至少添加一个上课时间");
                return;
            }
            draft.name = courseName;
            draft.code = code.getText().toString().trim();
            draft.location = location.getText().toString().trim();
            draft.teachers.clear();
            for (String t : teachers.getText().toString().split("[、,，;；/]+")) {
                if (!t.trim().isEmpty()) draft.teachers.add(t.trim());
            }
            if (isNew) table.courses.add(draft);
            else {
                for (int i = 0; i < table.courses.size(); i++) {
                    if (table.courses.get(i).id.equals(draft.id)) {
                        table.courses.set(i, draft);
                        break;
                    }
                }
            }
            store.save();
            sheet.dismiss();
            a.refresh();
            Ui.toast(a, isNew ? "课程已添加" : "课程已更新");
        });
        sheet.footer().addView(save);
        sheet.show();
    }

    private static void renderSwatches(MainActivity a, Palette p, LinearLayout wrap, Store.Course draft) {
        wrap.removeAllViews();
        for (int color : Palette.COURSE_COLORS) {
            FrameLayout cell = new FrameLayout(a);
            cell.setLayoutParams(new LinearLayout.LayoutParams(0, Ui.dp(a, 38), 1));
            View dot = new View(a);
            boolean selected = draft.color == color;
            dot.setBackground(Ui.stroke(a, color, selected ? p.ink : Color.TRANSPARENT,
                    selected ? 2.5f : 0, 16));
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(Ui.dp(a, 28), Ui.dp(a, 28));
            dlp.gravity = Gravity.CENTER;
            cell.addView(dot, dlp);
            cell.setClickable(true);
            cell.setOnClickListener(v -> {
                draft.color = color;
                renderSwatches(a, p, wrap, draft);
            });
            wrap.addView(cell);
        }
    }

    private static void renderSessions(final MainActivity a, final Palette p,
                                       final Store.Timetable table, LinearLayout list,
                                       final Store.Course draft) {
        list.removeAllViews();
        for (int i = 0; i < draft.sessions.size(); i++) {
            final int index = i;
            Store.Session s = draft.sessions.get(i);
            Store.TimeSlot slot = Store.slotById(table, s.slotId);
            LinearLayout row = Ui.row(a);
            row.setBackground(Ui.stroke(a, p.surfaceAlt, p.line, 1, 14));
            row.setPadding(Ui.dp(a, 11), Ui.dp(a, 9), Ui.dp(a, 6), Ui.dp(a, 9));
            TextView day = Ui.text(a, Dates.weekShort(s.day), 12, p.primary, true);
            day.setGravity(Gravity.CENTER);
            day.setBackground(Ui.bg(a, p.primarySoft, 9));
            day.setMinWidth(Ui.dp(a, 32));
            day.setMinHeight(Ui.dp(a, 32));
            row.addView(day);
            LinearLayout lines = Ui.column(a);
            lines.setPadding(Ui.dp(a, 10), 0, 0, 0);
            int endIdx = Store.sessionEndIndex(table, s);
            String time = slot == null ? "时间段已删除" : slot.span();
            if (slot != null && endIdx >= 0 && endIdx != Store.slotIndex(table, s.slotId)) {
                time += " – " + table.timeSlots.get(endIdx).span();
            }
            lines.addView(Ui.text(a, Store.sessionSlotLabel(table, s) + "  ·  " + time,
                    12.5f, p.ink, true));
            TextView w = Ui.text(a, weekLine(s), 10.5f, p.faint, false);
            w.setPadding(0, Ui.dp(a, 2), 0, 0);
            lines.addView(w);
            String sLoc = Store.effectiveLocation(draft, s);
            String sTeacher = Store.teacherLine(draft, s);
            if (!sLoc.isEmpty() || !sTeacher.isEmpty()) {
                TextView extra = Ui.text(a, (sLoc.isEmpty() ? "" : sLoc)
                        + (sLoc.isEmpty() || sTeacher.isEmpty() ? "" : " · ") + sTeacher,
                        10, p.sub, false);
                extra.setPadding(0, Ui.dp(a, 2), 0, 0);
                lines.addView(extra);
            }
            row.addView(lines, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            ImageView del = Ui.iconButton(a, R.drawable.ic_close, p.faint, p, false);
            del.setContentDescription("删除这个上课时间");
            del.setOnClickListener(v -> {
                draft.sessions.remove(index);
                renderSessions(a, p, table, list, draft);
                if (draft.sessions.isEmpty()) {
                    TextView hint = Ui.text(a, "还没有安排。点「添加」选择星期与时间段。", 12, p.faint, false);
                    hint.setPadding(0, Ui.dp(a, 8), 0, 0);
                    list.addView(hint);
                }
            });
            row.addView(del);
            row.setClickable(true);
            row.setContentDescription("编辑 " + Dates.weekFull(s.day) + " 的上课时间");
            row.setOnClickListener(v -> openSessionPicker(a, p, table, s, updated -> {
                for (int j = 0; j < draft.sessions.size(); j++) {
                    if (j != index && sessionsOverlap(table, draft.sessions.get(j), updated)) {
                        Ui.toast(a, "这个时间已经有安排了");
                        return;
                    }
                }
                draft.sessions.set(index, updated);
                renderSessions(a, p, table, list, draft);
            }));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(a, 7);
            list.addView(row, lp);
        }
    }

    private static Store.Course copyCourse(Store.Course c) {
        Store.Course copy = new Store.Course();
        copy.id = c.id;
        copy.name = c.name;
        copy.code = c.code;
        copy.location = c.location;
        copy.teachers = new ArrayList<>(c.teachers);
        copy.color = c.color;
        for (Store.Session s : c.sessions) {
            Store.Session ns = new Store.Session();
            ns.day = s.day;
            ns.slotId = s.slotId;
            ns.endSlotId = s.endSlotId;
            ns.location = s.location;
            ns.teachers = new ArrayList<>(s.teachers);
            ns.weeks = new ArrayList<>();
            for (Store.WeekRange r : s.effectiveWeeks()) {
                ns.weeks.add(new Store.WeekRange(r.start, r.end, r.parity));
            }
            ns.startWeek = s.startWeek;
            ns.endWeek = s.endWeek;
            ns.parity = s.parity;
            copy.sessions.add(ns);
        }
        return copy;
    }

    private static boolean sessionsOverlap(Store.Timetable table, Store.Session a, Store.Session b) {
        if (a.day != b.day) return false;
        if (!WeekSpec.overlaps(a.effectiveWeeks(), b.effectiveWeeks())) return false;
        int a1 = Store.slotIndex(table, a.slotId);
        int a2 = Store.sessionEndIndex(table, a);
        int b1 = Store.slotIndex(table, b.slotId);
        int b2 = Store.sessionEndIndex(table, b);
        return a1 <= b2 && b1 <= a2;
    }

    private static void openSessionPicker(MainActivity a, Palette p, Store.Timetable table,
                                          Store.Session initial, final SessionCallback callback) {
        if (table.timeSlots.isEmpty()) {
            Sheet.alert(a, p, "没有可用时间段", "请先在「时间段」里添加一节课的时间。");
            return;
        }
        Store.Session picked = new Store.Session();
        if (initial != null) {
            picked.day = initial.day;
            picked.slotId = initial.slotId;
            picked.endSlotId = initial.endSlotId;
            picked.location = initial.location;
            picked.teachers = new ArrayList<>(initial.teachers);
            for (Store.WeekRange r : initial.effectiveWeeks()) {
                picked.weeks.add(new Store.WeekRange(r.start, r.end, r.parity));
            }
            picked.startWeek = initial.startWeek;
            picked.endWeek = initial.endWeek;
            picked.parity = initial.parity;
        } else {
            picked.day = 1;
            picked.slotId = table.timeSlots.get(0).id;
            picked.endSlotId = "";
            picked.weeks = WeekSpec.one(1, 20, "all");
            picked.startWeek = 1;
            picked.endWeek = 20;
            picked.parity = "all";
        }
        Sheet sheet = new Sheet(a, p, initial == null ? "添加上课时间" : "编辑上课时间",
                "支持跨节次、多周段与单双周");

        sheet.label("星期");
        LinearLayout days = Ui.row(a);
        TextView[] dayChips = new TextView[8];
        for (int d = 1; d <= 7; d++) {
            final int day = d;
            TextView chip = Ui.chip(a, Dates.weekShort(d), p, picked.day == d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = Ui.dp(a, d == 7 ? 0 : 3);
            days.addView(chip, lp);
            dayChips[d] = chip;
            chip.setOnClickListener(v -> {
                picked.day = day;
                for (int j = 1; j <= 7; j++) Ui.setChipSelected(dayChips[j], j == day, p);
            });
        }
        sheet.add(days);

        final int[] startSel = {Math.max(0, Store.slotIndex(table, picked.slotId))};
        final int endIndex = picked.endSlotId == null || picked.endSlotId.isEmpty()
                ? startSel[0] : Math.max(startSel[0], Store.slotIndex(table, picked.endSlotId));
        final int[] endSel = {endIndex};

        sheet.label("开始节次");
        HorizontalScrollView startScroll = new HorizontalScrollView(a);
        startScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout startStrip = Ui.row(a);
        startScroll.addView(startStrip);
        TextView[] startChips = new TextView[table.timeSlots.size()];
        for (int i = 0; i < table.timeSlots.size(); i++) {
            TextView chip = Ui.text(a, String.valueOf(i + 1), 12.5f, p.sub, false);
            chip.setGravity(Gravity.CENTER);
            chip.setContentDescription("第 " + (i + 1) + " 节 " + table.timeSlots.get(i).span());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42));
            lp.rightMargin = Ui.dp(a, 4);
            startStrip.addView(chip, lp);
            startChips[i] = chip;
        }
        sheet.add(startScroll);

        sheet.label("结束节次");
        HorizontalScrollView endScroll = new HorizontalScrollView(a);
        endScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout endStrip = Ui.row(a);
        endScroll.addView(endStrip);
        TextView[] endChips = new TextView[table.timeSlots.size()];
        for (int i = 0; i < table.timeSlots.size(); i++) {
            final int index = i;
            TextView chip = Ui.text(a, String.valueOf(i + 1), 12.5f, p.sub, false);
            chip.setGravity(Gravity.CENTER);
            chip.setContentDescription("结束于第 " + (i + 1) + " 节");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42));
            lp.rightMargin = Ui.dp(a, 4);
            endStrip.addView(chip, lp);
            endChips[i] = chip;
            chip.setOnClickListener(v -> {
                if (index < startSel[0]) {
                    Ui.toast(a, "结束节次不能早于开始节次");
                    return;
                }
                endSel[0] = index;
                paintChips(endChips, endSel[0], p, a);
            });
        }
        for (int i = 0; i < startChips.length; i++) {
            final int index = i;
            startChips[i].setOnClickListener(v -> {
                startSel[0] = index;
                if (endSel[0] < index) endSel[0] = index;
                paintChips(startChips, startSel[0], p, a);
                paintChips(endChips, endSel[0], p, a);
            });
        }
        sheet.add(endScroll);
        paintChips(startChips, startSel[0], p, a);
        paintChips(endChips, endSel[0], p, a);

        sheet.label("周次");
        EditText weeks = Ui.input(a, "例如：1-10, 12-17 或 单周 1-3, 7-13", p, false);
        weeks.setText(WeekSpec.format(picked.effectiveWeeks()));
        sheet.add(weeks);
        TextView weeksHint = Ui.text(a, "支持单周 / 双周；用逗号分隔多个区间。", 10.5f, p.faint, false);
        weeksHint.setPadding(0, Ui.dp(a, 5), 0, 0);
        sheet.add(weeksHint);

        sheet.label("本时段地点（可留空）");
        EditText location = Ui.input(a, "留空沿用课程默认地点，例如：品学楼C216", p, false);
        location.setText(picked.location);
        sheet.add(location);

        sheet.label("本时段教师（可留空）");
        EditText teachers = Ui.input(a, "留空沿用课程默认教师，多位用顿号分隔", p, false);
        teachers.setText(String.join("、", picked.teachers));
        sheet.add(teachers);

        TextView confirm = Ui.primaryButton(a, initial == null ? "添加这个时间" : "保存", p);
        confirm.setOnClickListener(v -> {
            ArrayList<Store.WeekRange> parsed;
            try {
                parsed = WeekSpec.parse(weeks.getText().toString());
            } catch (Exception ex) {
                Ui.toast(a, ex.getMessage() == null ? "周次格式有误" : ex.getMessage());
                return;
            }
            if (parsed.isEmpty()) {
                Ui.toast(a, "请填写周次，例如 1-16");
                return;
            }
            picked.slotId = table.timeSlots.get(startSel[0]).id;
            picked.endSlotId = endSel[0] == startSel[0] ? "" : table.timeSlots.get(endSel[0]).id;
            picked.weeks = parsed;
            picked.startWeek = parsed.get(0).start;
            picked.endWeek = parsed.get(parsed.size() - 1).end;
            picked.parity = parsed.size() == 1 ? parsed.get(0).parity : "all";
            picked.location = location.getText().toString().trim();
            picked.teachers.clear();
            for (String t : teachers.getText().toString().split("[、,，;；/]+")) {
                if (!t.trim().isEmpty()) picked.teachers.add(t.trim());
            }
            callback.onSession(picked);
            sheet.dismiss();
        });
        sheet.addFooter(confirm);
        sheet.show();
    }

    private static void paintChips(TextView[] chips, int selected, Palette p, MainActivity a) {
        if (chips == null) return;
        for (int i = 0; i < chips.length; i++) {
            boolean on = i == selected;
            chips[i].setTextColor(on ? p.primary : p.sub);
            chips[i].setTypeface(on ? Ui.MEDIUM : Ui.REGULAR);
            chips[i].setBackground(on
                    ? Ui.stroke(a, p.primarySoft, p.primary, 1.4f, 11)
                    : Ui.pressable(a, p.surfaceAlt, Ui.dp(a, 11), Palette.alpha(p.ink, 15)));
        }
    }

    // ------------------------------------------------------------ slots

    public static void slotManager(MainActivity a) {
        Palette p = a.palette();
        Store store = a.store();
        Sheet sheet = new Sheet(a, p, "时间段", "一个时间段可以包含多个分段，例如课间休息");
        LinearLayout list = Ui.column(a);
        sheet.add(list);

        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            Store.Timetable table = store.data.timetable;
            if (table.timeSlots.isEmpty()) {
                list.addView(Ui.text(a, "还没有时间段。添加一节课的开始与结束时间。", 13, p.faint, false));
            }
            for (int i = 0; i < table.timeSlots.size(); i++) {
                Store.TimeSlot slot = table.timeSlots.get(i);
                LinearLayout row = Ui.row(a);
                row.setBackground(Ui.stroke(a, p.surfaceAlt, p.line, 1, 14));
                row.setPadding(Ui.dp(a, 12), Ui.dp(a, 9), Ui.dp(a, 5), Ui.dp(a, 9));
                TextView index = Ui.text(a, String.valueOf(i + 1), 12, p.primary, true);
                index.setGravity(Gravity.CENTER);
                index.setBackground(Ui.bg(a, p.primarySoft, 9));
                index.setMinWidth(Ui.dp(a, 30));
                index.setMinHeight(Ui.dp(a, 30));
                row.addView(index);
                LinearLayout lines = Ui.column(a);
                lines.setPadding(Ui.dp(a, 10), 0, 0, 0);
                lines.addView(Ui.text(a, slot.name, 13, p.ink, true));
                String segs = slot.segmentLabel();
                TextView seg = Ui.text(a, segs, 10.5f, p.faint, false);
                seg.setPadding(0, Ui.dp(a, 3), 0, 0);
                lines.addView(seg);
                row.addView(lines, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                ImageView edit = Ui.iconButton(a, R.drawable.ic_edit, p.sub, p, false);
                edit.setContentDescription("编辑时间段");
                edit.setOnClickListener(v -> openSlotEditor(a, sheet, slot));
                row.addView(edit);
                ImageView del = Ui.iconButton(a, R.drawable.ic_trash, p.faint, p, false);
                del.setContentDescription("删除时间段");
                del.setOnClickListener(v -> confirmDeleteSlot(a, sheet, slot, render[0]));
                row.addView(del);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(a, 7);
                list.addView(row, lp);
            }
        };
        render[0].run();

        TextView add = Ui.secondaryButton(a, "＋ 新建时间段", p);
        add.setOnClickListener(v -> openSlotEditor(a, sheet, null));
        sheet.addFooter(add);
        sheet.show();
    }

    private static void confirmDeleteSlot(MainActivity a, Sheet parent, Store.TimeSlot slot, Runnable after) {
        Palette p = a.palette();
        Store store = a.store();
        int used = 0;
        for (Store.Course c : store.data.timetable.courses) {
            for (Store.Session s : c.sessions) if (s.slotId.equals(slot.id)) used++;
        }
        String message = used == 0 ? "这个时间段没有被课程使用。"
                : "有 " + used + " 个上课安排正在使用这个时间段，删除后这些安排也会被移除。";
        Sheet.confirm(a, p, "删除时间段？", "「" + slot.name + "」\n" + message, "删除", true, () -> {
            store.data.timetable.timeSlots.remove(slot);
            for (Store.Course c : store.data.timetable.courses) {
                c.sessions.removeIf(s -> s.slotId.equals(slot.id));
            }
            store.save();
            after.run();
            a.refresh();
        });
    }

    private static void openSlotEditor(MainActivity a, Sheet parent, Store.TimeSlot existing) {
        Palette p = a.palette();
        Store store = a.store();
        boolean isNew = existing == null;
        Store.TimeSlot draft = new Store.TimeSlot();
        if (isNew) {
            draft.id = Store.newId("slot");
            draft.name = "第 " + (store.data.timetable.timeSlots.size() + 1) + " 节";
            draft.segments.add(new Store.Segment("08:00", "09:40"));
        } else {
            draft.id = existing.id;
            draft.name = existing.name;
            for (Store.Segment s : existing.segments) draft.segments.add(new Store.Segment(s.start, s.end));
        }
        Sheet sheet = new Sheet(a, p, isNew ? "新建时间段" : "编辑时间段",
                "一个时间段可以有多个分段，例如 08:00–09:40 与 10:00–11:40");

        EditText name = Ui.input(a, "时间段名称", p, false);
        name.setText(draft.name);
        sheet.label("名称");
        sheet.add(name);

        sheet.label("分段");
        LinearLayout segList = Ui.column(a);
        sheet.add(segList);
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            segList.removeAllViews();
            for (int i = 0; i < draft.segments.size(); i++) {
                Store.Segment seg = draft.segments.get(i);
                LinearLayout row = Ui.row(a);
                row.setBackground(Ui.stroke(a, p.surfaceAlt, p.line, 1, 13));
                row.setPadding(Ui.dp(a, 8), Ui.dp(a, 7), Ui.dp(a, 4), Ui.dp(a, 7));
                TextView start = Ui.text(a, seg.start, 13, p.ink, true);
                start.setGravity(Gravity.CENTER);
                start.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
                start.setBackground(Ui.bg(a, p.surface, 9));
                start.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                start.setOnClickListener(v -> Pickers.time(a, p, LocalTime.parse(seg.start), (h, m) -> {
                    seg.start = Dates.time(h * 60 + m);
                    render[0].run();
                }));
                row.addView(start);
                TextView dash = Ui.text(a, "—", 12, p.faint, false);
                dash.setPadding(Ui.dp(a, 5), 0, Ui.dp(a, 5), 0);
                row.addView(dash);
                TextView end = Ui.text(a, seg.end, 13, p.ink, true);
                end.setGravity(Gravity.CENTER);
                end.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
                end.setBackground(Ui.bg(a, p.surface, 9));
                end.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                end.setOnClickListener(v -> Pickers.time(a, p, LocalTime.parse(seg.end), (h, m) -> {
                    seg.end = Dates.time(h * 60 + m);
                    render[0].run();
                }));
                row.addView(end);
                ImageView del = Ui.iconButton(a, R.drawable.ic_close, p.faint, p, false);
                del.setContentDescription("删除分段");
                del.setOnClickListener(v -> {
                    if (draft.segments.size() <= 1) {
                        Ui.toast(a, "至少保留一个分段");
                        return;
                    }
                    draft.segments.remove(seg);
                    render[0].run();
                });
                row.addView(del);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(a, 7);
                segList.addView(row, lp);
            }
        };
        render[0].run();

        TextView addSeg = Ui.textButton(a, "＋ 添加分段", p.primary, p);
        addSeg.setOnClickListener(v -> {
            if (draft.segments.size() >= 4) {
                Ui.toast(a, "一个时间段最多 4 个分段");
                return;
            }
            String lastEnd = draft.segments.isEmpty() ? "10:00" : draft.segments.get(draft.segments.size() - 1).end;
            int m = Dates.minutes(lastEnd);
            int startM = Math.min(23 * 60, Math.max(0, m + 10));
            draft.segments.add(new Store.Segment(Dates.time(startM),
                    Dates.time(Math.min(23 * 60 + 55, startM + 45))));
            render[0].run();
        });
        sheet.add(addSeg);

        TextView save = Ui.primaryButton(a, isNew ? "创建时间段" : "保存", p);
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) n = "第 " + (store.data.timetable.timeSlots.size() + 1) + " 节";
            ArrayList<Store.Segment> sorted = new ArrayList<>(draft.segments);
            for (Store.Segment seg : sorted) {
                int s = Dates.minutes(seg.start);
                int e = Dates.minutes(seg.end);
                if (s < 0 || e < 0 || e <= s) {
                    Ui.toast(a, "结束时间必须晚于开始时间");
                    return;
                }
            }
            sorted.sort(Comparator.comparingInt(s -> Dates.minutes(s.start)));
            for (int i = 1; i < sorted.size(); i++) {
                if (Dates.minutes(sorted.get(i).start) < Dates.minutes(sorted.get(i - 1).end)) {
                    Ui.toast(a, "分段之间不能重叠");
                    return;
                }
            }
            draft.name = n;
            draft.segments = sorted;
            if (isNew) store.data.timetable.timeSlots.add(draft);
            else {
                for (int i = 0; i < store.data.timetable.timeSlots.size(); i++) {
                    if (store.data.timetable.timeSlots.get(i).id.equals(draft.id)) {
                        store.data.timetable.timeSlots.set(i, draft);
                        break;
                    }
                }
            }
            store.save();
            sheet.dismiss();
            parent.dismiss();
            a.refresh();
            Ui.toast(a, isNew ? "时间段已创建" : "时间段已保存");
        });
        sheet.footer().addView(save);
        sheet.show();
    }

    public interface SessionCallback {
        void onSession(Store.Session session);
    }
}
