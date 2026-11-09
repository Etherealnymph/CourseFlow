package com.etherealnymph.courseflow;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

public class AlarmScreen implements MainActivity.Screen {

    private final MainActivity a;
    private final Store store;
    private final Palette p;

    public AlarmScreen(MainActivity a) {
        this.a = a;
        this.store = a.store();
        this.p = a.palette();
    }

    @Override
    public View build() {
        LinearLayout root = Ui.column(a);
        LinearLayout header = Ui.column(a);
        header.setPadding(Ui.dp(a, 18), Ui.dp(a, 3), Ui.dp(a, 18), 0);
        root.addView(header);

        LinearLayout top = Ui.row(a);
        LinearLayout titles = Ui.column(a);
        titles.addView(Ui.eyebrow(a, "ALARM · 闹钟", p));
        int active = 0;
        for (Store.Alarm alarm : store.data.alarms) if (alarm.enabled) active++;
        TextView meta = Ui.text(a, active + " 个已开启 · 到点提醒", 10.5f, p.sub, false);
        meta.setPadding(0, Ui.dp(a, 2), 0, 0);
        titles.addView(meta);
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView calendar = Ui.iconButton(a, R.drawable.ic_calendar, p.sub, p, false);
        calendar.setContentDescription("节假日设置");
        calendar.setOnClickListener(v -> CalendarSheet.openHolidayManager(a, () -> a.refresh()));
        top.addView(calendar);
        ImageView add = Ui.iconButton(a, R.drawable.ic_plus, p.primary, p, true);
        add.setContentDescription("新建闹钟");
        add.setOnClickListener(v -> edit(null));
        top.addView(add);
        header.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = Ui.screenScroll(a, p);
        LinearLayout body = Ui.column(a);
        body.setPadding(Ui.dp(a, 18), Ui.dp(a, 8), Ui.dp(a, 18), Ui.dp(a, 28));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (!AlarmScheduler.canPostNotifications(a)) {
            LinearLayout banner = Ui.row(a);
            banner.setBackground(Ui.bg(a, p.accentSoft, 13));
            banner.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
            banner.addView(Ui.icon(a, R.drawable.ic_bell, 16, p.accent));
            TextView txt = Ui.text(a, "通知未开启，闹钟不会响", 11, p.sub, false);
            txt.setPadding(Ui.dp(a, 7), 0, Ui.dp(a, 7), 0);
            banner.addView(txt, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView open = Ui.textButton(a, "开启", p.accent, p);
            open.setOnClickListener(v -> a.maybeAskNotifications(true));
            banner.addView(open);
            body.addView(banner);
        }

        body.addView(nextCard());
        body.addView(Ui.spacer(a, 12));
        ArrayList<Store.Alarm> alarms = new ArrayList<>(store.data.alarms);
        alarms.sort(Comparator.comparingInt(x -> x.hour * 60 + x.minute));
        if (alarms.isEmpty()) {
            body.addView(Ui.emptyState(a, R.drawable.ic_alarm, "还没有闹钟",
                    "闹钟可以按工作日、非工作日或自定义日期响铃。", p));
            TextView create = Ui.primaryButton(a, "创建第一个闹钟", p);
            create.setOnClickListener(v -> edit(null));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Ui.dp(a, 190), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = Ui.dp(a, 4);
            body.addView(create, lp);
        } else {
            for (Store.Alarm alarm : alarms) body.addView(alarmCard(alarm), cardLp());
        }
        return root;
    }

    private View nextCard() {
        Store.Alarm next = null;
        long best = Long.MAX_VALUE;
        for (Store.Alarm alarm : store.data.alarms) {
            if (!alarm.enabled) continue;
            long at = ClockAlarmScheduler.nextTrigger(a, alarm);
            if (at > 0 && at < best) {
                best = at;
                next = alarm;
            }
        }
        LinearLayout card = Ui.row(a);
        card.setBackground(Ui.bg(a, p.primarySoft, 18));
        card.setPadding(Ui.dp(a, 16), Ui.dp(a, 13), Ui.dp(a, 16), Ui.dp(a, 13));
        FrameLayout halo = new FrameLayout(a);
        halo.setBackground(Ui.bg(a, p.primary, 12));
        ImageView icon = Ui.icon(a, R.drawable.ic_alarm, 21, p.onPrimary);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(a, 21), Ui.dp(a, 21));
        ilp.gravity = Gravity.CENTER;
        halo.addView(icon, ilp);
        card.addView(halo, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));
        LinearLayout lines = Ui.column(a);
        lines.setPadding(Ui.dp(a, 11), 0, 0, 0);
        if (next == null) {
            lines.addView(Ui.text(a, "没有即将响铃的闹钟", 13, p.sub, true));
            lines.addView(Ui.text(a, "新建一个，或打开已有闹钟", 10.5f, p.faint, false));
        } else {
            lines.addView(Ui.text(a, "下一个闹钟 · " + Dates.dueLabel(best), 14, p.ink, true));
            lines.addView(Ui.text(a, (next.label.isEmpty() ? "闹钟" : next.label)
                    + "  ·  " + ClockAlarmScheduler.modeLabel(next), 10.5f, p.sub, false));
            final long target = best;
            final TextView countdown = Ui.text(a, remainingText(target), 10.5f, p.primary, true);
            countdown.setPadding(0, Ui.dp(a, 4), 0, 0);
            lines.addView(countdown);
            final Handler handler = new Handler(Looper.getMainLooper());
            final Runnable tick = new Runnable() {
                @Override
                public void run() {
                    countdown.setText(remainingText(target));
                    handler.postDelayed(this, 1000);
                }
            };
            card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    handler.post(tick);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    handler.removeCallbacks(tick);
                }
            });
        }
        card.addView(lines, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return card;
    }

    private static String remainingText(long target) {
        long left = target - System.currentTimeMillis();
        if (left <= 0) return "正在响铃";
        long totalSeconds = left / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder("还有 ");
        if (days > 0) sb.append(days).append(" 天 ");
        if (days > 0 || hours > 0) sb.append(hours).append(" 小时 ");
        sb.append(minutes).append(" 分 ").append(seconds).append(" 秒");
        return sb.toString();
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 9);
        return lp;
    }

    private LinearLayout alarmCard(Store.Alarm alarm) {
        LinearLayout card = Ui.row(a);
        card.setBackground(Ui.stroke(a, p.surface, p.line, 1, 18));
        card.setPadding(Ui.dp(a, 14), Ui.dp(a, 11), Ui.dp(a, 12), Ui.dp(a, 11));
        if (!alarm.enabled) card.setAlpha(0.58f);
        LinearLayout time = Ui.column(a);
        TextView hm = Ui.text(a, String.format(Locale.CHINA, "%02d:%02d", alarm.hour, alarm.minute),
                25, p.ink, true);
        time.addView(hm);
        TextView mode = Ui.text(a, ClockAlarmScheduler.modeLabel(alarm), 9.5f, p.faint, false);
        mode.setMaxLines(1);
        time.addView(mode);
        card.addView(time, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout side = Ui.column(a);
        side.setGravity(Gravity.END);
        TextView toggle = Ui.chip(a, alarm.enabled ? "已开启" : "关闭", p, alarm.enabled);
        toggle.setOnClickListener(v -> {
            alarm.enabled = !alarm.enabled;
            store.save();
            ClockAlarmScheduler.schedule(a, alarm);
            a.refresh();
        });
        side.addView(toggle);
        long next = alarm.enabled ? ClockAlarmScheduler.nextTrigger(a, alarm) : 0;
        if (next > 0) {
            TextView n = Ui.text(a, Dates.dueLabel(next), 9, p.faint, false);
            n.setPadding(0, Ui.dp(a, 4), 0, 0);
            side.addView(n);
        }
        card.addView(side);
        card.setClickable(true);
        card.setOnClickListener(v -> edit(alarm));
        return card;
    }

    private void edit(Store.Alarm existing) {
        boolean isNew = existing == null;
        Store.Alarm draft = new Store.Alarm();
        if (isNew) {
            draft.id = Store.newId("alarm");
            draft.label = "起床";
            draft.hour = 7;
            draft.minute = 30;
            draft.mode = "workday";
            draft.weekdays = 0x1F;
            draft.repeatCount = 1;
            draft.repeatIntervalMin = 5;
            draft.ringSeconds = 30;
            draft.vibrate = false;
        } else {
            draft.id = existing.id;
            draft.label = existing.label;
            draft.hour = existing.hour;
            draft.minute = existing.minute;
            draft.enabled = existing.enabled;
            draft.mode = existing.mode;
            draft.weekdays = existing.weekdays;
            draft.startDate = existing.startDate;
            draft.endDate = existing.endDate;
            draft.extraDates = new ArrayList<>(existing.extraDates);
            draft.repeatCount = Math.max(1, existing.repeatCount);
            draft.repeatIntervalMin = Math.max(1, existing.repeatIntervalMin);
            draft.ringSeconds = Math.max(5, existing.ringSeconds);
            draft.vibrate = existing.vibrate;
        }
        Sheet sheet = new Sheet(a, p, isNew ? "新建闹钟" : "编辑闹钟",
                "工作日 / 非工作日 / 自定义日期");
        TextView timeBtn = Ui.primaryButton(a,
                String.format(Locale.CHINA, "%02d:%02d", draft.hour, draft.minute), p);
        timeBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30);
        timeBtn.setOnClickListener(v -> Pickers.time(a, p, LocalTime.of(draft.hour, draft.minute),
                (h, m) -> {
                    draft.hour = h;
                    draft.minute = m;
                    timeBtn.setText(String.format(Locale.CHINA, "%02d:%02d", h, m));
                }));
        sheet.add(timeBtn);

        sheet.label("重复与响铃");
        LinearLayout repeatRow = Ui.row(a);
        TextView repeatBtn = Ui.secondaryButton(a, "响 " + draft.repeatCount + " 次", p);
        TextView intervalBtn = Ui.secondaryButton(a, "间隔 " + draft.repeatIntervalMin + " 分", p);
        TextView ringBtn = Ui.secondaryButton(a, "响铃 " + draft.ringSeconds + " 秒", p);
        repeatBtn.setOnClickListener(v -> Pickers.number(a, p, "重复次数", "次", 1, 10,
                draft.repeatCount, n -> {
                    draft.repeatCount = n;
                    repeatBtn.setText("响 " + n + " 次");
                }));
        intervalBtn.setOnClickListener(v -> Pickers.number(a, p, "重复间隔", "分钟", 1, 60,
                draft.repeatIntervalMin, n -> {
                    draft.repeatIntervalMin = n;
                    intervalBtn.setText("间隔 " + n + " 分");
                }));
        ringBtn.setOnClickListener(v -> Pickers.number(a, p, "响铃时长", "秒", 5, 120,
                draft.ringSeconds, n -> {
                    draft.ringSeconds = n;
                    ringBtn.setText("响铃 " + n + " 秒");
                }));
        repeatRow.addView(repeatBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        ilp.leftMargin = Ui.dp(a, 5);
        repeatRow.addView(intervalBtn, ilp);
        LinearLayout.LayoutParams rlp2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        rlp2.leftMargin = Ui.dp(a, 5);
        repeatRow.addView(ringBtn, rlp2);
        sheet.add(repeatRow);
        LinearLayout vibrateRow = Ui.row(a);
        vibrateRow.setPadding(0, Ui.dp(a, 7), 0, 0);
        vibrateRow.addView(Ui.text(a, "响铃时同时震动", 11.5f, p.sub, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView vibrate = Ui.chip(a, "震动", p, draft.vibrate);
        vibrate.setOnClickListener(v -> {
            draft.vibrate = !draft.vibrate;
            Ui.setChipSelected(vibrate, draft.vibrate, p);
        });
        vibrateRow.addView(vibrate);
        sheet.add(vibrateRow);

        sheet.label("名称");
        EditText label = Ui.input(a, "例如：起床 / 吃药", p, false);
        label.setText(draft.label);
        sheet.add(label);

        sheet.label("重复方式");
        LinearLayout modes = Ui.row(a);
        String[] values = {"workday", "offday", "workday_plus", "custom"};
        String[] labels = {"工作日", "非工作日", "工作日+日期", "自定义"};
        TextView[] chips = new TextView[4];
        final LinearLayout[] customBox = new LinearLayout[1];
        for (int i = 0; i < 4; i++) {
            final String value = values[i];
            TextView chip = Ui.chip(a, labels[i], p, value.equals(draft.mode));
            chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f);
            chip.setPadding(Ui.dp(a, 5), Ui.dp(a, 8), Ui.dp(a, 5), Ui.dp(a, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = Ui.dp(a, i == 3 ? 0 : 3);
            modes.addView(chip, lp);
            chips[i] = chip;
            chip.setOnClickListener(v -> {
                draft.mode = value;
                for (int j = 0; j < 4; j++) Ui.setChipSelected(chips[j], values[j].equals(value), p);
                if (customBox[0] != null) customBox[0].setVisibility(
                        ("custom".equals(value) || "workday_plus".equals(value))
                                ? View.VISIBLE : View.GONE);
            });
        }
        sheet.add(modes);

        LinearLayout custom = Ui.column(a);
        customBox[0] = custom;
        custom.setVisibility(("custom".equals(draft.mode) || "workday_plus".equals(draft.mode))
                ? View.VISIBLE : View.GONE);
        custom.setPadding(0, Ui.dp(a, 6), 0, 0);
        custom.addView(Ui.text(a, "星期（仅自定义模式生效）", 11, p.sub, true));
        LinearLayout weekdays = Ui.row(a);
        String[] dnames = {"一", "二", "三", "四", "五", "六", "日"};
        TextView[] dchips = new TextView[7];
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            TextView chip = Ui.chip(a, dnames[i], p, (draft.weekdays & bit) != 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = Ui.dp(a, i == 6 ? 0 : 3);
            weekdays.addView(chip, lp);
            dchips[i] = chip;
            chip.setOnClickListener(v -> {
                draft.weekdays ^= bit;
                Ui.setChipSelected(chip, (draft.weekdays & bit) != 0, p);
            });
        }
        custom.addView(weekdays);
        TextView hint = Ui.text(a, "额外日期（可写 2026-11-09, 2026-11-10）", 10.5f, p.faint, false);
        hint.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        custom.addView(hint);
        EditText extra = Ui.input(a, "在工作日基础上额外响铃的日期", p, false);
        extra.setText(String.join(", ", draft.extraDates));
        custom.addView(extra);
        sheet.add(custom);

        sheet.label("生效范围（可留空）");
        LinearLayout range = Ui.row(a);
        final String[] startDate = {draft.startDate};
        final String[] endDate = {draft.endDate};
        TextView startBtn = Ui.secondaryButton(a, startDate[0].isEmpty() ? "开始日期" : startDate[0], p);
        TextView endBtn = Ui.secondaryButton(a, endDate[0].isEmpty() ? "结束日期" : endDate[0], p);
        startBtn.setGravity(Gravity.CENTER);
        endBtn.setGravity(Gravity.CENTER);
        startBtn.setOnClickListener(v -> Pickers.date(a, p, Dates.today(), d -> {
            startDate[0] = Dates.date(d);
            startBtn.setText(startDate[0]);
        }));
        endBtn.setOnClickListener(v -> Pickers.date(a, p, Dates.today().plusMonths(1), d -> {
            endDate[0] = Dates.date(d);
            endBtn.setText(endDate[0]);
        }));
        range.addView(startBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        rlp.leftMargin = Ui.dp(a, 8);
        range.addView(endBtn, rlp);
        sheet.add(range);
        TextView clearRange = Ui.textButton(a, "清除范围", p.faint, p);
        clearRange.setOnClickListener(v -> {
            startDate[0] = "";
            endDate[0] = "";
            startBtn.setText("开始日期");
            endBtn.setText("结束日期");
        });
        sheet.add(clearRange);

        LinearLayout enabledRow = Ui.row(a);
        enabledRow.setPadding(0, Ui.dp(a, 12), 0, 0);
        enabledRow.addView(Ui.text(a, "启用这个闹钟", 12, p.ink, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView enabled = Ui.chip(a, draft.enabled ? "已开启" : "关闭", p, draft.enabled);
        enabled.setOnClickListener(v -> {
            draft.enabled = !draft.enabled;
            enabled.setText(draft.enabled ? "已开启" : "关闭");
            Ui.setChipSelected(enabled, draft.enabled, p);
        });
        enabledRow.addView(enabled);
        sheet.add(enabledRow);

        TextView save = Ui.primaryButton(a, isNew ? "保存闹钟" : "保存修改", p);
        save.setOnClickListener(v -> {
            draft.label = label.getText().toString().trim();
            draft.extraDates.clear();
            for (String s : extra.getText().toString().split("[,，、;；\\s]+")) {
                LocalDate d = Dates.parseDate(s.trim());
                if (d != null) draft.extraDates.add(Dates.date(d));
            }
            draft.startDate = startDate[0];
            draft.endDate = endDate[0];
            if (isNew) store.data.alarms.add(draft);
            else {
                for (int i = 0; i < store.data.alarms.size(); i++) {
                    if (store.data.alarms.get(i).id.equals(draft.id)) {
                        store.data.alarms.set(i, draft);
                        break;
                    }
                }
            }
            store.save();
            ClockAlarmScheduler.schedule(a, draft);
            sheet.dismiss();
            a.refresh();
            Ui.toast(a, isNew ? "闹钟已创建" : "闹钟已保存");
        });
        if (!isNew) {
            TextView del = Ui.dangerButton(a, "删除", p);
            del.setOnClickListener(v -> Sheet.confirm(a, p, "删除这个闹钟？", "保存后将不再响铃。",
                    "删除", true, () -> {
                        ClockAlarmScheduler.cancel(a, draft);
                        store.data.alarms.removeIf(x -> x.id.equals(draft.id));
                        store.save();
                        sheet.dismiss();
                        a.refresh();
                    }));
            sheet.actions(del, save);
        } else {
            sheet.addFooter(save);
        }
        sheet.show();
    }
}
