package com.etherealnymph.courseflow;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;

public class SettingsScreen implements MainActivity.Screen {

    private final MainActivity a;
    private final Store store;
    private final Palette p;

    public SettingsScreen(MainActivity a) {
        this.a = a;
        this.store = a.store();
        this.p = a.palette();
    }

    @Override
    public View build() {
        ScrollView scroll = Ui.screenScroll(a, p);
        LinearLayout body = Ui.column(a);
        body.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 34));
        scroll.addView(body);

        body.addView(Ui.eyebrow(a, "PREFERENCES · 设置", p));

        body.addView(hero(), topLp(8));

        body.addView(section("外观"), topLp(24));
        body.addView(themeGroup(), topLp(9));

        body.addView(section("课程表"), topLp(24));
        body.addView(courseGroup(), topLp(9));

        body.addView(section("笔记与待办"), topLp(24));
        body.addView(dataGroup(), topLp(9));

        body.addView(section("提醒与记录"), topLp(24));
        body.addView(reminderGroup(), topLp(9));

        body.addView(section("关于"), topLp(24));
        body.addView(aboutGroup(), topLp(9));

        TextView companion = buildCompanion();
        companion.setPadding(0, Ui.dp(a, 24), 0, 0);
        body.addView(companion);
        TextView footer = Ui.caption(a, "时屿 COURSEFLOW  ·  v1.2.2  ·  JSON schema v2", p);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, Ui.dp(a, 5), 0, 0);
        body.addView(footer);
        return scroll;
    }

    private TextView buildCompanion() {
        TextView tv = Ui.text(a, "", 10.5f, p.faint, false);
        tv.setGravity(Gravity.CENTER);
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            long seconds = Math.max(0, (System.currentTimeMillis() - store.data.createdAt) / 1000);
            long days = seconds / 86400;
            long years = days / 365;
            long months = (days % 365) / 30;
            long day = days % 30;
            long hours = (seconds % 86400) / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;
            tv.setText("时屿已陪伴你 " + years + " 年 " + months + " 个月 " + day + " 天 "
                    + hours + " 时 " + minutes + " 分 " + secs + " 秒");
            handler.postDelayed(tick[0], 1000);
        };
        tv.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                handler.removeCallbacks(tick[0]);
                handler.post(tick[0]);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                handler.removeCallbacks(tick[0]);
            }
        });
        return tv;
    }

    private LinearLayout.LayoutParams topLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, top);
        return lp;
    }

    private View section(String label) {
        LinearLayout row = Ui.row(a);
        row.setPadding(Ui.dp(a, 2), 0, Ui.dp(a, 2), 0);
        row.addView(Ui.section(a, label, p), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    // --------------------------------------------------------------- hero

    private View hero() {
        Store.Timetable t = store.data.timetable;
        LocalDate term = Dates.parseDate(t.termStart);
        int current = term == null ? 0 : Dates.weekOf(term, Dates.today());
        LocalDate monday = term == null ? Dates.mondayOf(Dates.today())
                : Dates.weekStart(term, Math.max(1, current));

        LinearLayout card = Ui.column(a);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{p.primarySoft, p.surface});
        bg.setCornerRadius(Ui.dp(a, 22));
        bg.setStroke(Ui.dp(a, 1), p.line);
        card.setBackground(bg);
        card.setPadding(Ui.dp(a, 17), Ui.dp(a, 15), Ui.dp(a, 17), Ui.dp(a, 15));

        LinearLayout top = Ui.row(a);
        LinearLayout titles = Ui.column(a);
        titles.addView(Ui.text(a, t.name, 13, p.sub, true));
        String week = current > 0 ? "第 " + current + " 周" : (current == 0 ? "学期尚未开始" : "学期已结束");
        TextView big = Ui.text(a, week, 30, p.ink, true);
        big.setPadding(0, Ui.dp(a, 4), 0, 0);
        titles.addView(big);
        titles.addView(Ui.text(a, "第一周 · " + t.termStart + "  ·  " + Dates.weekRange(monday),
                11, p.faint, false));
        top.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView edit = Ui.chip(a, "编辑", p, true);
        edit.setOnClickListener(v -> TimetableSheets.editTerm(a));
        top.addView(edit);
        card.addView(top);

        int totalWeeks = 20;
        int progress = Math.max(0, Math.min(totalWeeks, current));
        LinearLayout bar = Ui.row(a);
        bar.setBackground(Ui.bg(a, Palette.alpha(p.ink, 10), 3));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 5));
        blp.topMargin = Ui.dp(a, 14);
        View filled = new View(a);
        filled.setBackground(Ui.bg(a, p.primary, 3));
        bar.addView(filled, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, progress));
        bar.addView(new View(a), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, totalWeeks - progress));
        card.addView(bar, blp);
        return card;
    }

    // -------------------------------------------------------------- theme

    private View themeGroup() {
        LinearLayout group = Ui.row(a);
        group.setBackground(Ui.stroke(a, p.surface, p.line, 1, 18));
        group.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        String mode = store.data.theme == null ? "system" : store.data.theme;
        group.addView(themeOption("自动", R.drawable.ic_settings, "system".equals(mode), "system"));
        group.addView(themeOption("日间", R.drawable.ic_sun, "light".equals(mode), "light"));
        group.addView(themeOption("夜间", R.drawable.ic_moon, "dark".equals(mode), "dark"));
        return group;
    }

    private View themeOption(String label, int iconRes, boolean selected, String mode) {
        LinearLayout cell = Ui.column(a);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 17));
        cell.setBackground(selected ? Ui.stroke(a, p.primarySoft, p.primary, 1.3f, 13)
                : Ui.pressable(a, Color.TRANSPARENT, Ui.dp(a, 13), Palette.alpha(p.ink, 14)));
        cell.addView(Ui.icon(a, iconRes, 21, selected ? p.primary : p.faint));
        TextView tv = Ui.text(a, label, 12, selected ? p.primary : p.sub, selected);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, Ui.dp(a, 9), 0, Ui.dp(a, 2));
        cell.addView(tv);
        cell.setClickable(true);
        cell.setOnClickListener(v -> a.setThemeMode(mode));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(Ui.dp(a, 2), 0, Ui.dp(a, 2), 0);
        cell.setLayoutParams(lp);
        return cell;
    }

    // ------------------------------------------------------------ course

    private View courseGroup() {
        LinearLayout group = group();
        addRow(group, R.drawable.ic_calendar, "学期与第一周",
                store.data.timetable.name + "  ·  " + store.data.timetable.termStart, null, false,
                () -> TimetableSheets.editTerm(a));
        addDivider(group);
        addRow(group, R.drawable.ic_clock, "时间段",
                store.data.timetable.timeSlots.size() + " 个时间段 · 支持分段时间", null, false,
                () -> TimetableSheets.slotManager(a));
        addDivider(group);
        addRow(group, R.drawable.ic_download, "导入课程表",
                "从 JSON 文件替换当前课程表", null, false, a::importTimetable);
        addDivider(group);
        addRow(group, R.drawable.ic_upload, "导出课程表",
                "生成可编辑、可备份的 JSON 文件", null, false, a::exportTimetable);
        addDivider(group);
        addRow(group, R.drawable.ic_info, "JSON 格式",
                "查看字段说明与完整示例", null, false, () -> showTimetableFormat(a));
        addDivider(group);
        addRow(group, R.drawable.ic_calendar, "载入本学期课表",
                "2026–2027 第一学期 · 11 节作息", null, false, a::loadSemesterTimetable);
        return group;
    }

    private View dataGroup() {
        LinearLayout group = group();
        int priv = store.data.vault == null ? 0 : store.data.vault.notes.size();
        addRow(group, R.drawable.ic_upload, "导出笔记与待办",
                "普通内容明文，私密内容以密文备份", null, false, a::exportNotes);
        addDivider(group);
        addRow(group, R.drawable.ic_download, "导入笔记与待办",
                "支持合并或替换", null, false, a::importNotes);
        addDivider(group);
        addRow(group, R.drawable.ic_shield, "私密笔记",
                store.data.vault == null ? "尚未设置密码" : "已加密 " + priv + " 条，需要密码查看",
                store.data.vault == null ? "设置" : "查看", false, () -> {
                    a.notesOpenPrivate = true;
                    a.setTab(MainActivity.TAB_NOTES);
                });
        addDivider(group);
        addRow(group, R.drawable.ic_mic, "录音保存目录",
                "新录音保存到手机的 " + store.data.recordingDir,
                null, false, this::chooseRecordingDir);
        addDivider(group);
        addRow(group, R.drawable.ic_download, "导入待办清单",
                "支持 todo_list JSON 并自动识别时间", null, false, a::importTodoList);
        return group;
    }

    private View reminderGroup() {
        LinearLayout group = group();
        boolean notify = AlarmScheduler.canPostNotifications(a);
        boolean exact = AlarmScheduler.canScheduleExact(a);
        addRow(group, R.drawable.ic_alarm, "闹钟",
                "工作日 / 非工作日 / 自定义日期", null, false,
                () -> a.showTab(MainActivity.TAB_ALARM, true));
        addDivider(group);
        addRow(group, R.drawable.ic_calendar, "节假日与调休",
                store.data.holidays.size() + " 条自定义日期 · 支持 JSON 导入", null, false,
                () -> CalendarSheet.openHolidayManager(a, () -> a.refresh()));
        addDivider(group);
        addRow(group, R.drawable.ic_bell, "通知权限",
                notify ? "已开启，会显示待办提醒" : "未开启，到点不会弹窗",
                notify ? "已开启" : "去开启", !notify, a::openNotificationSettings);
        addDivider(group);
        addRow(group, R.drawable.ic_clock, "精确提醒",
                exact ? "已允许在准确时间提醒" : "未授权，提醒可能稍有延迟",
                exact ? "已允许" : "去设置", false, a::openExactAlarmSettings);
        addDivider(group);
        addRow(group, R.drawable.ic_spark, "发送测试通知",
                "检查通知渠道与权限是否正常", null, false, a::testNotification);
        return group;
    }

    private View aboutGroup() {
        LinearLayout group = group();
        addRow(group, R.drawable.ic_info, "版本",
                "时屿 1.2.2 (versionCode 5)", "Android 14", false, () -> showAbout());
        addDivider(group);
        addRow(group, R.drawable.ic_shield, "数据与隐私",
                "所有数据只写入应用私有目录，不联网", null, false, this::showPrivacy);
        addDivider(group);
        addRow(group, R.drawable.ic_trash, "清空全部数据",
                "课程、笔记、待办与私密空间都会被删除", null, true, this::confirmReset);
        return group;
    }

    private LinearLayout group() {
        LinearLayout group = Ui.column(a);
        group.setBackground(Ui.stroke(a, p.surface, p.line, 1, 18));
        group.setClipToOutline(true);
        return group;
    }

    private void addDivider(LinearLayout group) {
        View d = new View(a);
        d.setBackgroundColor(p.line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 1));
        lp.leftMargin = Ui.dp(a, 58);
        group.addView(d, lp);
    }

    private void addRow(LinearLayout group, int iconRes, String title, String subtitle,
                        String status, boolean danger, Runnable action) {
        LinearLayout row = Ui.row(a);
        row.setPadding(Ui.dp(a, 13), Ui.dp(a, 11), Ui.dp(a, 12), Ui.dp(a, 11));
        row.setMinimumHeight(Ui.dp(a, 58));
        row.setBackground(Ui.pressable(a, Color.TRANSPARENT, 0, Palette.alpha(p.ink, 14)));
        FrameLayout wrap = new FrameLayout(a);
        wrap.setBackground(Ui.bg(a, danger ? p.accentSoft : p.surfaceAlt, 11));
        ImageView icon = Ui.icon(a, iconRes, 18, danger ? p.danger : p.primary);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18));
        ilp.gravity = Gravity.CENTER;
        wrap.addView(icon, ilp);
        row.addView(wrap, new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 36)));
        LinearLayout lines = Ui.column(a);
        lines.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 8), 0);
        lines.addView(Ui.text(a, title, 13.5f, danger ? p.danger : p.ink, true));
        TextView s = Ui.text(a, subtitle, 10.8f, p.faint, false);
        s.setPadding(0, Ui.dp(a, 3), 0, 0);
        lines.addView(s);
        row.addView(lines, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (status != null) {
            TextView badge = Ui.pill(a, status, danger ? p.danger : p.primary, p);
            row.addView(badge);
        } else {
            row.addView(Ui.icon(a, R.drawable.ic_chevron_right, 17, p.faint));
        }
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
        group.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ------------------------------------------------------------- about

    private void chooseRecordingDir() {
        Sheet sheet = new Sheet(a, p, "录音保存目录", "只影响之后的新录音，已有录音不会移动");
        String[][] options = {
                {"Music/时屿", "音乐 / 时屿（推荐）"},
                {"Music", "音乐根目录"},
                {"Download/时屿", "下载 / 时屿"},
                {"Music/Recordings/时屿", "音乐 / Recordings / 时屿"}
        };
        LinearLayout list = Ui.column(a);
        for (String[] opt : options) {
            boolean selected = opt[0].equals(store.data.recordingDir);
            LinearLayout row = Ui.row(a);
            row.setBackground(Ui.stroke(a, selected ? p.primarySoft : p.surface,
                    selected ? p.primary : p.line, 1, 12));
            row.setPadding(Ui.dp(a, 12), Ui.dp(a, 11), Ui.dp(a, 12), Ui.dp(a, 11));
            LinearLayout info = Ui.column(a);
            info.addView(Ui.text(a, opt[1], 13, selected ? p.primary : p.ink, true));
            info.addView(Ui.text(a, "手机存储 / " + opt[0], 10, p.faint, false));
            row.addView(info, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (selected) row.addView(Ui.text(a, "✓", 14, p.primary, true));
            row.setClickable(true);
            row.setOnClickListener(v -> {
                store.data.recordingDir = opt[0];
                store.save();
                sheet.dismiss();
                a.refresh();
                Ui.toast(a, "新录音将保存到 " + opt[0]);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(a, 8);
            list.addView(row, lp);
        }
        sheet.add(list);
        TextView hint = Ui.text(a, "这些目录会直接出现在手机的文件管理器/音乐应用中。",
                10.5f, p.faint, false);
        hint.setPadding(0, Ui.dp(a, 4), 0, 0);
        sheet.add(hint);
        sheet.show();
    }

    private void showAbout() {
        Sheet sheet = new Sheet(a, p, "关于时屿", "COURSEFLOW · v1.2.2");
        LinearLayout art = new LinearLayout(a);
        art.setOrientation(LinearLayout.HORIZONTAL);
        art.setGravity(Gravity.CENTER);
        art.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 18));
        FrameLayout mark = new FrameLayout(a);
        mark.setBackground(Ui.bg(a, p.primary, 16));
        TextView letter = Ui.text(a, "课", 28, p.onPrimary, true);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.gravity = Gravity.CENTER;
        mark.addView(letter, llp);
        art.addView(mark, new LinearLayout.LayoutParams(Ui.dp(a, 64), Ui.dp(a, 64)));
        sheet.add(art);
        TextView intro = Ui.text(a, "一款安静、离线优先的课程表与个人任务工具。周课表、日程、笔记与待办被放在同一个容器里，数据只属于你。",
                13, p.sub, false);
        intro.setGravity(Gravity.CENTER);
        intro.setLineSpacing(Ui.dp(a, 4), 1f);
        sheet.add(intro);
        sheet.label("能力");
        sheet.add(Ui.text(a, "· 周课表 / 日课程任务 / 周课程任务\n· 多教师、分段时间、学期第一周\n· 日间与夜间两套完整配色\n· 笔记与待办搜索、时间提醒\n· AES-256-GCM 私密笔记\n· 课程表与笔记待办 JSON 导入导出",
                12.5f, p.ink, false));
        sheet.label("版本");
        sheet.add(Ui.text(a, "1.2.2  ·  versionCode 5  ·  targetSdk 34 (Android 14)", 12, p.faint, false));
        TextView close = Ui.primaryButton(a, "关闭", p);
        close.setOnClickListener(v -> sheet.dismiss());
        sheet.footer().addView(close);
        sheet.show();
    }

    private void showPrivacy() {
        Sheet sheet = new Sheet(a, p, "数据与隐私", "没有服务器，也没有账号");
        TextView body = Ui.text(a,
                "· 课程表、笔记、待办、设置都保存在应用私有目录的 courseflow.json 中。\n\n"
                        + "· 应用不申请网络权限，不会上传任何内容。\n\n"
                        + "· 私密笔记的标题和正文使用 AES-256-GCM 加密，密钥由密码经 PBKDF2-HMAC-SHA256 派生，仅存在于解锁后的内存中。\n\n"
                        + "· 导出文件由你选择保存位置。加密私密笔记的导出只包含密文，密码不会写入文件。\n\n"
                        + "· 卸载应用会删除本地数据，请先导出备份。",
                13, p.sub, false);
        body.setLineSpacing(Ui.dp(a, 4), 1f);
        sheet.add(body);
        TextView close = Ui.primaryButton(a, "知道了", p);
        close.setOnClickListener(v -> sheet.dismiss());
        sheet.footer().addView(close);
        sheet.show();
    }

    private void confirmReset() {
        Sheet.confirm(a, p, "清空全部数据？",
                "课程表、笔记、待办、私密空间与导入的设置都会被删除。这个操作不可撤销，建议先导出备份。",
                "清空全部", true, () -> {
                    for (Store.Todo t : store.data.todos) AlarmScheduler.cancel(a, t);
                    store.data.timetable = new Store.Timetable();
                    store.data.notes.clear();
                    store.data.todos.clear();
                    store.data.vault = null;
                    store.save();
                    a.scheduleWeekOffset = 0;
                    a.agendaWeekOffset = 0;
                    a.agendaWeekMode = false;
                    a.agendaSelectedDate = Dates.date(Dates.today());
                    a.showTab(MainActivity.TAB_SETTINGS, true);
                    Ui.toast(a, "全部数据已清空");
                });
    }

    // --------------------------------------------------------- json format

    public static void showTimetableFormat(MainActivity a) {
        Palette p = a.palette();
        Sheet sheet = new Sheet(a, p, "课程表 JSON", "schema: courseflow.timetable  ·  version 1");
        TextView intro = Ui.text(a, "字段说明", 13, p.ink, true);
        sheet.add(intro);
        TextView desc = Ui.text(a,
                "termStartDate：第一周开始日（任意一天，按所在周的周一计算）\n"
                        + "timeSlots[].segments：一个时间段可包含多个分段\n"
                        + "courses[].teachers：字符串数组，可多位教师\n"
                        + "sessions[].dayOfWeek：1–7，周一到周日\n"
                        + "sessions[].weekParity：all / odd / even",
                11.5f, p.sub, false);
        desc.setLineSpacing(Ui.dp(a, 3), 1f);
        desc.setPadding(0, Ui.dp(a, 7), 0, Ui.dp(a, 10));
        sheet.add(desc);

        TextView code = new TextView(a);
        code.setText(a.store().timetableJson());
        code.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9.5f);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextColor(p.sub);
        code.setBackground(Ui.bg(a, p.surfaceAlt, 12));
        code.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        code.setTextIsSelectable(true);
        code.setHorizontallyScrolling(false);
        sheet.add(code);

        TextView copy = Ui.primaryButton(a, "复制 JSON", p);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("courseflow.timetable", a.store().timetableJson()));
            Ui.toast(a, "已复制到剪贴板");
        });
        TextView export = Ui.secondaryButton(a, "导出文件", p);
        export.setOnClickListener(v -> {
            sheet.dismiss();
            a.exportTimetable();
        });
        sheet.actions(export, copy);
        sheet.show();
    }
}
