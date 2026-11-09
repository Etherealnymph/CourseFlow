package com.etherealnymph.courseflow;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.util.List;
import java.util.Locale;

public class TodosScreen implements MainActivity.Screen {

    private final MainActivity a;
    private final Store store;
    private final Palette p;
    private final boolean openNew;

    private LinearLayout root;
    private FrameLayout contentHost;
    private LinearLayout listHost;
    private EditText search;
    private String query = "";
    private int filter = 0; // 0 active, 1 all, 2 done

    public TodosScreen(MainActivity a, boolean openNew) {
        this.a = a;
        this.store = a.store();
        this.p = a.palette();
        this.openNew = openNew;
    }

    @Override
    public View build() {
        root = Ui.column(a);
        rebuildAll();
        if (openNew) root.post(() -> editTodo(null));
        return root;
    }

    private void rebuildAll() {
        if (root == null) return;
        root.removeAllViews();
        root.addView(header(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(filterBar());
        root.addView(searchArea());
        contentHost = new FrameLayout(a);
        root.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        ScrollView scroll = Ui.screenScroll(a, p);
        LinearLayout body = Ui.column(a);
        body.setPadding(Ui.dp(a, 20), Ui.dp(a, 4), Ui.dp(a, 20), Ui.dp(a, 30));
        listHost = body;
        scroll.addView(body);
        contentHost.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        renderList();
    }

    private View header() {
        LinearLayout header = Ui.column(a);
        header.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), 0);
        LinearLayout top = Ui.row(a);
        LinearLayout titles = Ui.column(a);
        titles.addView(Ui.eyebrow(a, "TASKS · 待办", p));
        int done = 0;
        for (Store.Todo t : store.data.todos) if (t.done) done++;
        int active = store.data.todos.size() - done;
        TextView meta = Ui.text(a, active + " 项待完成  ·  " + done + " 项已完成",
                11.5f, p.faint, false);
        meta.setPadding(0, Ui.dp(a, 2), 0, 0);
        titles.addView(meta);
        top.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView add = Ui.iconButton(a, R.drawable.ic_plus, p.primary, p, true);
        add.setContentDescription("新建待办");
        add.setOnClickListener(v -> editTodo(null));
        top.addView(add);
        ImageView more = Ui.iconButton(a, R.drawable.ic_more, p.sub, p, false);
        more.setContentDescription("待办操作");
        more.setOnClickListener(v -> showMore());
        top.addView(more);
        header.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (store.data.todos.size() > 0) {
            LinearLayout progress = Ui.row(a);
            progress.setBackground(Ui.bg(a, p.surfaceAlt, 3));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 4));
            plp.setMargins(0, Ui.dp(a, 13), 0, 0);
            header.addView(progress, plp);
            View filled = new View(a);
            filled.setBackground(Ui.bg(a, p.primary, 3));
            progress.addView(filled, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, done));
            View rest = new View(a);
            progress.addView(rest, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0, store.data.todos.size() - done)));
        }

        if (!AlarmScheduler.canPostNotifications(a)) {
            LinearLayout banner = Ui.row(a);
            banner.setBackground(Ui.bg(a, p.accentSoft, 13));
            banner.setPadding(Ui.dp(a, 11), Ui.dp(a, 8), Ui.dp(a, 11), Ui.dp(a, 8));
            banner.addView(Ui.icon(a, R.drawable.ic_bell, 16, p.accent));
            TextView txt = Ui.text(a, "通知未开启，待办到点不会弹窗", 11, p.sub, false);
            txt.setPadding(Ui.dp(a, 7), 0, Ui.dp(a, 7), 0);
            banner.addView(txt, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView open = Ui.textButton(a, "开启", p.accent, p);
            open.setOnClickListener(v -> a.maybeAskNotifications(true));
            banner.addView(open);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = Ui.dp(a, 10);
            header.addView(banner, blp);
        }
        return header;
    }

    private View filterBar() {
        LinearLayout bar = Ui.row(a);
        bar.setBackground(Ui.bg(a, p.surfaceAlt, 13));
        bar.setPadding(Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3));
        String[] labels = {"待完成", "全部", "已完成"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            int count = i == 0 ? count(false) : i == 2 ? count(true) : store.data.todos.size();
            TextView tv = Ui.text(a, labels[i] + " " + count, 12,
                    filter == i ? p.ink : p.faint, filter == i);
            tv.setGravity(Gravity.CENTER);
            tv.setBackground(filter == i ? Ui.stroke(a, p.surface, p.line, 1, 10)
                    : Ui.pressable(a, Color.TRANSPARENT, Ui.dp(a, 10), Palette.alpha(p.ink, 14)));
            tv.setOnClickListener(v -> {
                filter = index;
                rebuildAll();
            });
            bar.addView(tv, new LinearLayout.LayoutParams(0, Ui.dp(a, 34), 1));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(a, 20), Ui.dp(a, 14), Ui.dp(a, 20), 0);
        bar.setLayoutParams(lp);
        return bar;
    }

    private int count(boolean done) {
        int n = 0;
        for (Store.Todo t : store.data.todos) if (t.done == done) n++;
        return n;
    }

    private View searchArea() {
        LinearLayout row = Ui.searchBar(a, "搜索待办标题或备注", p);
        search = Ui.searchField(row);
        if (search != null) {
            search.setText(query);
            search.setSelection(search.getText().length());
            search.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    query = s.toString();
                    renderList();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(a, 20), Ui.dp(a, 10), Ui.dp(a, 20), Ui.dp(a, 6));
        row.setLayoutParams(lp);
        return row;
    }

    private void renderList() {
        if (listHost == null) return;
        listHost.removeAllViews();
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Store.Todo> list = new ArrayList<>();
        for (Store.Todo t : store.data.todos) {
            if (filter == 0 && t.done) continue;
            if (filter == 2 && !t.done) continue;
            if (!q.isEmpty() && !contains(t.title, q) && !contains(t.note, q)) continue;
            list.add(t);
        }
        list.sort(Comparator.comparing((Store.Todo t) -> t.done)
                .thenComparing(t -> t.done ? -(t.doneAt == 0 ? t.createdAt : t.doneAt)
                        : (t.dueAt == 0 ? Long.MAX_VALUE : t.dueAt)));
        if (list.isEmpty()) {
            String title = q.isEmpty()
                    ? (filter == 2 ? "还没有完成的待办" : "这里很清静")
                    : "没有找到相关待办";
            String sub = q.isEmpty() ? "添加一项待办，到时间会提醒你。" : "换一个关键词试试。";
            listHost.addView(Ui.emptyState(a, R.drawable.ic_todo, title, sub, p));
            if (q.isEmpty() && filter != 2) {
                TextView add = Ui.primaryButton(a, "新建待办", p);
                add.setOnClickListener(v -> editTodo(null));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        Ui.dp(a, 170), ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.CENTER_HORIZONTAL;
                lp.topMargin = Ui.dp(a, 4);
                listHost.addView(add, lp);
            }
            return;
        }

        boolean hasOverdueHeading = false;
        boolean hasUpcomingHeading = false;
        boolean hasDoneHeading = false;
        for (Store.Todo todo : list) {
            if (todo.done) {
                if (!hasDoneHeading) {
                    listHost.addView(sectionLabel(filter == 2 ? "已完成" : "已完成"), sectionLp());
                    hasDoneHeading = true;
                }
            } else if (isOverdue(todo)) {
                if (!hasOverdueHeading) {
                    listHost.addView(sectionLabel("已逾期"), sectionLp());
                    hasOverdueHeading = true;
                }
            } else {
                if (!hasUpcomingHeading) {
                    listHost.addView(sectionLabel("待完成"), sectionLp());
                    hasUpcomingHeading = true;
                }
            }
            listHost.addView(todoCard(todo), cardLp());
        }
    }

    private LinearLayout sectionLabel(String label) {
        LinearLayout row = Ui.row(a);
        row.addView(Ui.section(a, label, p), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private LinearLayout.LayoutParams sectionLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 14);
        return lp;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 8);
        return lp;
    }

    private LinearLayout todoCard(Store.Todo todo) {
        LinearLayout card = Ui.row(a);
        card.setBackground(Ui.stroke(a, p.surface, p.line, 1, 18));
        card.setPadding(Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 13), Ui.dp(a, 12));
        if (todo.done) card.setAlpha(0.68f);

        ImageView check = Ui.icon(a, todo.done ? R.drawable.ic_check_circle : R.drawable.ic_circle,
                23, todo.done ? p.primary : (isOverdue(todo) ? p.danger : p.faint));
        LinearLayout checkWrap = Ui.row(a);
        checkWrap.setPadding(Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 9), Ui.dp(a, 5));
        checkWrap.addView(check);
        checkWrap.setClickable(true);
        checkWrap.setContentDescription(todo.done ? "标记为未完成" : "标记为已完成");
        checkWrap.setOnClickListener(v -> toggle(todo));
        card.addView(checkWrap);

        LinearLayout info = Ui.column(a);
        if (todo.priority > 0 && !todo.done) {
            TextView pri = Ui.pill(a, todo.priority >= 2 ? "重要" : "优先",
                    todo.priority >= 2 ? p.accent : p.gold, p);
            info.addView(pri);
        }
        TextView title = Ui.text(a, todo.title, 14.5f, todo.done ? p.faint : p.ink, true);
        if (todo.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        title.setPadding(0, todo.priority > 0 && !todo.done ? Ui.dp(a, 5) : 0, 0, 0);
        info.addView(title);
        String preview = WeekSpec.stripForPreview(todo.note).replace('\n', ' ');
        if (!preview.isEmpty() || (todo.attachments != null && !todo.attachments.isEmpty())) {
            if (todo.attachments != null && !todo.attachments.isEmpty()) {
                if (!preview.isEmpty()) preview += "  ·  ";
                preview += todo.attachments.size() + " 个附件";
            }
            TextView note = Ui.text(a, preview, 11.5f, p.sub, false);
            note.setMaxLines(2);
            note.setEllipsize(android.text.TextUtils.TruncateAt.END);
            note.setPadding(0, Ui.dp(a, 5), 0, 0);
            info.addView(note);
        }
        LinearLayout dueRow = Ui.row(a);
        dueRow.setPadding(0, Ui.dp(a, 8), 0, 0);
        if (todo.dueAt > 0 || todo.remindAt > 0) {
            int color = todo.done ? p.faint : isOverdue(todo) ? p.danger
                    : (Dates.dateOf(todo.dueAt > 0 ? todo.dueAt : todo.remindAt).equals(Dates.today())
                    ? p.primary : p.faint);
            dueRow.addView(Ui.icon(a, R.drawable.ic_clock, 13, color));
            StringBuilder label = new StringBuilder();
            if (todo.done) label.append("已完成 · ");
            if (todo.remindAt > 0) {
                label.append("开始 ").append(Dates.dueLabel(todo.remindAt));
                if (todo.dueAt > 0) label.append("  ·  ");
            }
            if (todo.dueAt > 0) label.append("截止 ").append(Dates.dueLabel(todo.dueAt));
            TextView due = Ui.text(a, label.toString(), 10.2f, color, false);
            due.setPadding(Ui.dp(a, 4), 0, 0, 0);
            dueRow.addView(due);
        } else {
            dueRow.addView(Ui.text(a, "无提醒时间", 10.5f, p.faint, false));
        }
        info.addView(dueRow);
        card.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.setClickable(true);
        card.setOnClickListener(v -> editTodo(todo));
        return card;
    }

    private void editTodo(Store.Todo existing) {
        boolean isNew = existing == null;
        Store.Todo draft = new Store.Todo();
        if (isNew) {
            draft.id = Store.newId("todo");
            draft.createdAt = System.currentTimeMillis();
            int hour = LocalTime.now().getHour();
            draft.dueAt = hour >= 22
                    ? Dates.at(Dates.today().plusDays(1), 9, 0)
                    : Dates.at(Dates.today(), hour + 1, 0);
        } else {
            draft.id = existing.id;
            draft.title = existing.title;
            draft.note = existing.note;
            draft.remindAt = existing.remindAt;
            draft.dueAt = existing.dueAt;
            draft.done = existing.done;
            draft.createdAt = existing.createdAt;
            draft.doneAt = existing.doneAt;
            draft.priority = existing.priority;
            if (existing.attachments != null) draft.attachments = new ArrayList<>(existing.attachments);
        }
        Sheet sheet = new Sheet(a, p, isNew ? "新建待办" : "编辑待办",
                "支持起止提醒与简易格式标记");
        EditText title = Ui.input(a, "要完成什么？", p, false);
        title.setText(draft.title);
        sheet.label("事项");
        sheet.add(title);
        RichEditText note = new RichEditText(a, p, draft.attachments);
        note.setHint("备注");
        note.setMinHeight(Ui.dp(a, 110));
        note.setMarkup(draft.note);
        RichEditorHelper rich = new RichEditorHelper(a, note, draft.attachments);
        sheet.label("备注");
        sheet.add(rich.toolbar());
        sheet.add(note);
        sheet.add(rich.strip());
        sheet.add(rich.preview());

        final ReminderBlock start = new ReminderBlock("开始提醒", draft.remindAt,
                Dates.today(), 19, 30);
        final ReminderBlock due = new ReminderBlock("截止时间", draft.dueAt,
                Dates.today(), 21, 0);
        sheet.add(start.view());
        sheet.add(due.view());

        sheet.label("提前提醒");
        LinearLayout advanceRow = Ui.row(a);
        TextView advanceBtn = Ui.secondaryButton(a,
                advanceLabel(draft.advanceMinutes), p);
        advanceBtn.setOnClickListener(v -> Pickers.number(a, p, "提前提醒", "分钟", 0, 360,
                draft.advanceMinutes, n -> {
                    draft.advanceMinutes = n;
                    advanceBtn.setText(advanceLabel(n));
                }));
        advanceRow.addView(advanceBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView alarmChip = Ui.chip(a, "响铃", p, draft.advanceAlarm);
        alarmChip.setOnClickListener(v -> {
            draft.advanceAlarm = !draft.advanceAlarm;
            Ui.setChipSelected(alarmChip, draft.advanceAlarm, p);
        });
        advanceRow.addView(alarmChip);
        sheet.add(advanceRow);

        final TextView detected = Ui.text(a, "", 10.5f, p.primary, false);
        detected.setPadding(0, Ui.dp(a, 6), 0, 0);
        sheet.add(detected);
        final Runnable autoDetect = () -> {
            if (due.userTouched) return;
            String text = title.getText().toString() + " " + note.getText().toString();
            long at = TimeTextParser.parse(text);
            if (at > 0) {
                due.setMoment(at);
                detected.setText("已识别时间：" + Dates.dueFull(at) + "（可修改）");
            }
        };
        note.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) autoDetect.run();
        });
        title.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) autoDetect.run();
        });

        sheet.label("优先级");
        LinearLayout priority = Ui.row(a);
        String[] plabels = {"普通", "优先", "重要"};
        TextView[] pchips = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int value = i;
            TextView chip = Ui.chip(a, plabels[i], p, draft.priority == i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = Ui.dp(a, i == 2 ? 0 : 4);
            priority.addView(chip, lp);
            pchips[i] = chip;
            chip.setOnClickListener(v -> {
                draft.priority = value;
                for (int j = 0; j < 3; j++) Ui.setChipSelected(pchips[j], j == value, p);
            });
        }
        sheet.add(priority);

        TextView save = Ui.primaryButton(a, isNew ? "添加待办" : "保存修改", p);
        save.setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            if (t.isEmpty()) {
                Ui.toast(a, "请填写待办事项");
                title.requestFocus();
                return;
            }
            draft.title = t;
            draft.note = note.getMarkup();
            draft.attachments = rich.attachments();
            if (!due.userTouched) {
                long detectedAt = TimeTextParser.parse(draft.title + " " + draft.note);
                if (detectedAt > 0) due.setMoment(detectedAt);
            }
            draft.remindAt = start.millis();
            draft.dueAt = due.millis();
            if (isNew) store.data.todos.add(draft);
            else {
                for (int i = 0; i < store.data.todos.size(); i++) {
                    if (store.data.todos.get(i).id.equals(draft.id)) {
                        store.data.todos.set(i, draft);
                        break;
                    }
                }
            }
            store.save();
            if (draft.done) AlarmScheduler.cancel(a, draft);
            else AlarmScheduler.schedule(a, draft);
            if ((draft.dueAt > 0 && draft.dueAt <= System.currentTimeMillis())
                    || (draft.remindAt > 0 && draft.remindAt <= System.currentTimeMillis())) {
                Ui.toast(a, "有提醒时间已经过去，该提醒不会弹窗");
            }
            sheet.dismiss();
            if (!AlarmScheduler.canPostNotifications(a)) a.maybeAskNotifications(false);
            rebuildAll();
            Ui.toast(a, isNew ? "待办已添加" : "待办已更新");
        });

        if (!isNew) {
            TextView del = Ui.dangerButton(a, "删除", p);
            del.setOnClickListener(v -> Sheet.confirm(a, p, "删除这项待办？",
                    "「" + draft.title + "」的提醒也会取消。", "删除", true, () -> {
                        AlarmScheduler.cancel(a, draft);
                        store.data.todos.removeIf(t -> t.id.equals(draft.id));
                        store.save();
                        sheet.dismiss();
                        rebuildAll();
                    }));
            sheet.actions(del, save);
        } else {
            sheet.footer().addView(save);
        }
        sheet.show();
    }

    private void toggle(Store.Todo todo) {
        todo.done = !todo.done;
        todo.doneAt = todo.done ? System.currentTimeMillis() : 0;
        store.save();
        if (todo.done) AlarmScheduler.cancel(a, todo);
        else AlarmScheduler.schedule(a, todo);
        rebuildAll();
    }

    private void showMore() {
        Sheet sheet = new Sheet(a, p, "待办", "提醒与数据");
        ScheduleScreen.addAction(sheet, R.drawable.ic_spark, "载入本学期待办清单",
                "内置给出的 todo_list 示例 · 自动识别时间", () -> {
                    sheet.dismiss();
                    a.importBuiltInTodos();
                });
        ScheduleScreen.addAction(sheet, R.drawable.ic_download, "导入清单 JSON",
                "支持 todo_list 格式，自动识别时间", () -> {
                    sheet.dismiss();
                    a.importTodoList();
                });
        ScheduleScreen.addAction(sheet, R.drawable.ic_download, "导入笔记与待办备份",
                "从 courseflow.notes JSON 导入", () -> {
                    sheet.dismiss();
                    a.importNotes();
                });
        ScheduleScreen.addAction(sheet, R.drawable.ic_upload, "导出笔记与待办",
                "JSON 备份，包含私密笔记密文", () -> {
                    sheet.dismiss();
                    a.exportNotes();
                });
        sheet.divider();
        ScheduleScreen.addAction(sheet, R.drawable.ic_bell, "通知设置",
                AlarmScheduler.canPostNotifications(a) ? "通知权限已开启" : "通知权限未开启", () -> {
                    sheet.dismiss();
                    a.openNotificationSettings();
                });
        ScheduleScreen.addAction(sheet, R.drawable.ic_clock, "精确提醒",
                AlarmScheduler.canScheduleExact(a) ? "已允许精确闹钟" : "未授权，提醒可能稍有延迟", () -> {
                    sheet.dismiss();
                    a.openExactAlarmSettings();
                });
        ScheduleScreen.addAction(sheet, R.drawable.ic_spark, "发送测试通知", "立即检查提醒是否可见", () -> {
            sheet.dismiss();
            a.testNotification();
        });
        sheet.show();
    }

    private class ReminderBlock {
        final LinearLayout root;
        final TextView toggle;
        final TextView dateBtn;
        final TextView timeBtn;
        final LinearLayout dateRow;
        boolean enabled;
        boolean userTouched;
        LocalDate date;
        int hour;
        int minute;

        ReminderBlock(String label, long initial, LocalDate fallbackDate, int fallbackHour,
                      int fallbackMinute) {
            enabled = initial > 0;
            date = initial > 0 ? Dates.dateOf(initial) : fallbackDate;
            hour = initial > 0 ? Dates.timeOf(initial).getHour() : fallbackHour;
            minute = initial > 0 ? Dates.timeOf(initial).getMinute() : fallbackMinute;

            root = Ui.column(a);
            LinearLayout head = Ui.row(a);
            head.setPadding(0, Ui.dp(a, 13), 0, 0);
            head.addView(Ui.text(a, label, 12, p.ink, true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            toggle = Ui.chip(a, "关闭", p, false);
            head.addView(toggle);
            root.addView(head);

            dateRow = Ui.row(a);
            dateBtn = Ui.secondaryButton(a, "", p);
            timeBtn = Ui.secondaryButton(a, "", p);
            dateBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            dateRow.addView(dateBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            tlp.leftMargin = Ui.dp(a, 8);
            dateRow.addView(timeBtn, tlp);
            root.addView(dateRow);

            toggle.setOnClickListener(v -> {
                enabled = !enabled;
                userTouched = true;
                update();
            });
            dateBtn.setOnClickListener(v -> {
                if (!enabled) return;
                Pickers.date(a, p, date, d -> {
                    date = d;
                    userTouched = true;
                    update();
                });
            });
            timeBtn.setOnClickListener(v -> {
                if (!enabled) return;
                Pickers.time(a, p, LocalTime.of(hour, minute), (h, m) -> {
                    hour = h;
                    minute = m;
                    userTouched = true;
                    update();
                });
            });
            update();
        }

        View view() {
            return root;
        }

        boolean isEnabled() {
            return enabled;
        }

        long millis() {
            return enabled ? Dates.at(date, hour, minute) : 0;
        }

        void setMoment(long at) {
            if (at <= 0) return;
            enabled = true;
            date = Dates.dateOf(at);
            hour = Dates.timeOf(at).getHour();
            minute = Dates.timeOf(at).getMinute();
            update();
        }

        void update() {
            dateBtn.setText(Dates.monthDay(date) + " · " + Dates.weekFull(Dates.dow(date)));
            timeBtn.setText(String.format(Locale.CHINA, "%02d:%02d", hour, minute));
            toggle.setText(enabled ? "已开启" : "关闭");
            Ui.setChipSelected(toggle, enabled, p);
            dateRow.setAlpha(enabled ? 1f : 0.42f);
            dateBtn.setEnabled(enabled);
            timeBtn.setEnabled(enabled);
        }
    }

    private static String advanceLabel(int minutes) {
        if (minutes <= 0) return "关闭提前提醒";
        if (minutes % 60 == 0) return "提前 " + (minutes / 60) + " 小时";
        return "提前 " + minutes + " 分钟";
    }

    private boolean isOverdue(Store.Todo t) {
        return !t.done && t.dueAt > 0 && t.dueAt < System.currentTimeMillis();
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    @Override
    public boolean onBack() {
        if (query != null && !query.isEmpty()) {
            query = "";
            if (search != null) search.setText("");
            renderList();
            return true;
        }
        if (filter != 0) {
            filter = 0;
            rebuildAll();
            return true;
        }
        return false;
    }
}
