package com.etherealnymph.courseflow;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    public static final int TAB_SCHEDULE = 0;
    public static final int TAB_AGENDA = 1;
    public static final int TAB_NOTES = 2;
    public static final int TAB_TODOS = 3;
    public static final int TAB_ALARM = 4;
    public static final int TAB_RECORDINGS = 5;
    public static final int TAB_SETTINGS = 6;

    private static final int REQ_NOTIFICATIONS = 90;
    private static final int REQ_EXPORT_TIMETABLE = 101;
    private static final int REQ_IMPORT_TIMETABLE = 102;
    private static final int REQ_EXPORT_NOTES = 103;
    private static final int REQ_IMPORT_NOTES = 104;
    private static final int REQ_IMPORT_HOLIDAYS = 105;
    private static final int REQ_IMPORT_TODO_LIST = 106;
    private static final int REQ_PICK_IMAGE = 107;
    private static final int REQ_CAPTURE_IMAGE = 108;
    private static final int REQ_PICK_FILE = 109;
    private static final int REQ_RECORD_AUDIO = 91;

    public interface AttachmentSink {
        void onAttachment(Store.Attachment attachment);
    }

    private AttachmentSink attachmentSink;
    private Uri cameraOutput;
    private boolean attachmentPickerActive;
    private Runnable pendingRecordAction;

    private Store store;
    private Palette p;
    private boolean dark;
    private int tab = TAB_SCHEDULE;
    private FrameLayout contentFrame;
    private LinearLayout stack;
    private FrameLayout navHost;
    private LinearLayout navBar;
    private final List<NavItem> navItems = new ArrayList<>();
    private Screen screen;
    private NotesScreen notesScreen;
    private boolean openNewTodo;

    private Store.Timetable pendingTimetable;
    private Store.NotesBundle pendingNotes;

    public int scheduleWeekOffset;
    public int agendaWeekOffset;
    public String agendaSelectedDate = Dates.date(Dates.today());
    public boolean agendaWeekMode;
    public boolean notesOpenPrivate;

    public interface Screen {
        View build();

        default boolean onBack() {
            return false;
        }

        default void onStop() {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);
        dark = resolveDark(store.data.theme);
        p = new Palette(dark);
        edgeToEdge();
        buildShell();
        if (savedInstanceState != null) {
            tab = savedInstanceState.getInt("tab", TAB_SCHEDULE);
            scheduleWeekOffset = savedInstanceState.getInt("schedule_week", 0);
            agendaWeekOffset = savedInstanceState.getInt("agenda_week", 0);
            agendaWeekMode = savedInstanceState.getBoolean("agenda_week_mode", false);
            agendaSelectedDate = savedInstanceState.getString("agenda_date", agendaSelectedDate);
        }
        String requested = getIntent() == null ? null : getIntent().getStringExtra(AlarmScheduler.EXTRA_TAB);
        if ("todos".equals(requested)) tab = TAB_TODOS;
        else if ("notes".equals(requested)) tab = TAB_NOTES;
        else if ("alarm".equals(requested)) tab = TAB_ALARM;
        showTab(tab, false);
        Ui.styleSystemBars(this, p);
        if (!getSharedPreferences("courseflow", MODE_PRIVATE).getBoolean("intro_done", false)) {
            getWindow().getDecorView().postDelayed(this::showWelcome, 420);
        }
    }

    private void showWelcome() {
        if (isFinishing() || isDestroyed()) return;
        Sheet sheet = new Sheet(this, p, "欢迎来到时屿", "COURSEFLOW · 把一周安排得从容一点");
        LinearLayout hero = Ui.row(this);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 18));
        FrameLayout mark = new FrameLayout(this);
        mark.setBackground(Ui.bg(this, p.primary, 20));
        TextView letter = Ui.text(this, "课", 34, p.onPrimary, true);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.gravity = Gravity.CENTER;
        mark.addView(letter, mlp);
        hero.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 78), Ui.dp(this, 78)));
        sheet.add(hero);

        TextView intro = Ui.text(this,
                "周课表、日程、笔记与待办，都在同一个安静的容器里。应用完全离线运行，数据保存在本机。\n\n"
                        + "现在可以先放一份示例数据熟悉界面，也可以从一张空白课表开始。",
                13, p.sub, false);
        intro.setLineSpacing(Ui.dp(this, 4), 1f);
        sheet.add(intro);

        TextView sample = Ui.primaryButton(this, "看看示例", p);
        sample.setOnClickListener(v -> {
            getSharedPreferences("courseflow", MODE_PRIVATE).edit().putBoolean("intro_done", true).apply();
            sheet.dismiss();
            maybeAskNotifications(false);
        });
        TextView blank = Ui.secondaryButton(this, "从空白开始", p);
        blank.setOnClickListener(v -> {
            getSharedPreferences("courseflow", MODE_PRIVATE).edit().putBoolean("intro_done", true).apply();
            for (Store.Todo todo : store.data.todos) AlarmScheduler.cancel(this, todo);
            store.data.timetable = new Store.Timetable();
            store.data.notes.clear();
            store.data.todos.clear();
            store.data.vault = null;
            store.save();
            sheet.dismiss();
            showTab(TAB_SCHEDULE, true);
            maybeAskNotifications(false);
            Ui.toast(this, "已创建空白课表");
        });
        sheet.actions(blank, sample);
        TextView later = Ui.textButton(this, "稍后决定，先看看示例", p.faint, p);
        later.setOnClickListener(v -> {
            getSharedPreferences("courseflow", MODE_PRIVATE).edit().putBoolean("intro_done", true).apply();
            sheet.dismiss();
            maybeAskNotifications(false);
        });
        sheet.addFooter(later);
        sheet.dialog().setOnDismissListener(d ->
                getSharedPreferences("courseflow", MODE_PRIVATE).edit().putBoolean("intro_done", true).apply());
        sheet.show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", tab);
        outState.putInt("schedule_week", scheduleWeekOffset);
        outState.putInt("agenda_week", agendaWeekOffset);
        outState.putBoolean("agenda_week_mode", agendaWeekMode);
        outState.putString("agenda_date", agendaSelectedDate);
    }

    private boolean resolveDark(String mode) {
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        int night = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void edgeToEdge() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(p.bg);
        root.addView(new BackdropView(this, p), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        root.addView(stack, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        contentFrame = new FrameLayout(this);
        contentFrame.setClipChildren(false);
        stack.addView(contentFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        navHost = new FrameLayout(this);
        navHost.setClipChildren(false);
        navHost.setPadding(Ui.dp(this, 12), Ui.dp(this, 5), Ui.dp(this, 12), Ui.dp(this, 11));
        stack.addView(navHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
        navBar.setBackground(Ui.stroke(this, p.surface, p.line, 1, 24));
        navBar.setElevation(Ui.dp(this, 12));
        navHost.addView(navBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 62)));
        buildNavItems();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
                boolean keyboard = insets.isVisible(WindowInsets.Type.ime()) && ime.bottom > 0;
                if (keyboard) {
                    stack.setPadding(0, top, 0, ime.bottom);
                    navHost.setVisibility(View.GONE);
                } else {
                    stack.setPadding(0, top, 0, 0);
                    navHost.setVisibility(View.VISIBLE);
                    navHost.setPadding(Ui.dp(this, 12), Ui.dp(this, 5),
                            Ui.dp(this, 12), Ui.dp(this, 11) + bottom);
                }
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
                stack.setPadding(0, top, 0, 0);
                navHost.setPadding(Ui.dp(this, 12), Ui.dp(this, 5),
                        Ui.dp(this, 12), Ui.dp(this, 11) + bottom);
            }
            contentFrame.setPadding(left, 0, right, 0);
            return insets;
        });

        setContentView(root);
    }

    private void buildNavItems() {
        navItems.clear();
        navBar.removeAllViews();
        addNav(0, R.drawable.ic_calendar, "课表");
        addNav(1, R.drawable.ic_agenda, "日程");
        addNav(2, R.drawable.ic_note, "笔记");
        addNav(3, R.drawable.ic_todo, "待办");
        addNav(4, R.drawable.ic_alarm, "闹钟");
        addNav(5, R.drawable.ic_mic, "录音");
        addNav(6, R.drawable.ic_settings, "设置");
    }

    private void addNav(int index, int iconRes, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> showTab(index, true));

        FrameLayout holder = new FrameLayout(this);
        holder.setBackground(Ui.bg(this, Color.TRANSPARENT, 14));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 27));
        item.addView(holder, hlp);
        ImageView icon = Ui.icon(this, iconRes, 21, p.faint);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(this, 21), Ui.dp(this, 21));
        ilp.gravity = Gravity.CENTER;
        holder.addView(icon, ilp);

        TextView tv = Ui.text(this, label, 9.5f, p.faint, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(this, 3);
        item.addView(tv, tlp);
        item.setContentDescription(label);
        navBar.addView(item, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        navItems.add(new NavItem(item, holder, icon, tv, index));
    }

    private void updateNav() {
        for (NavItem n : navItems) {
            boolean selected = n.index == tab;
            n.holder.setBackground(Ui.bg(this, selected ? p.primarySoft : Color.TRANSPARENT, 14));
            n.icon.setColorFilter(selected ? p.primary : p.faint);
            n.label.setTextColor(selected ? p.primary : p.faint);
            n.label.setTypeface(selected ? Ui.MEDIUM : Ui.REGULAR);
        }
    }

    public void showTab(int index, boolean animate) {
        if (screen != null) screen.onStop();
        // Any tab transition or explicit refresh drops the decrypted private
        // note session; a new screen starts locked.
        if (notesScreen != null) notesScreen.lockNow();
        tab = Math.max(0, Math.min(6, index));
        updateNav();
        hideKeyboard();
        Screen next;
        switch (tab) {
            case TAB_AGENDA:
                next = new AgendaScreen(this);
                break;
            case TAB_NOTES:
                notesScreen = new NotesScreen(this);
                next = notesScreen;
                break;
            case TAB_TODOS:
                next = new TodosScreen(this, openNewTodo);
                openNewTodo = false;
                break;
            case TAB_ALARM:
                next = new AlarmScreen(this);
                break;
            case TAB_RECORDINGS:
                next = new RecordingsScreen(this);
                break;
            case TAB_SETTINGS:
                next = new SettingsScreen(this);
                break;
            case TAB_SCHEDULE:
            default:
                next = new ScheduleScreen(this);
                break;
        }
        screen = next;
        View view = screen.build();
        contentFrame.removeAllViews();
        contentFrame.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (animate) {
            view.setAlpha(0f);
            view.setTranslationY(Ui.dp(this, 10));
            view.animate().alpha(1f).translationY(0f).setDuration(230)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        if (tab == TAB_TODOS) maybeAskNotifications(false);
    }

    public void refresh() {
        showTab(tab, false);
    }

    public void setTab(int index) {
        showTab(index, true);
    }

    public void openNewTodo() {
        openNewTodo = true;
        showTab(TAB_TODOS, true);
    }

    public void openTimetable() {
        showTab(TAB_SCHEDULE, true);
    }

    public Store store() {
        return store;
    }

    public Palette palette() {
        return p;
    }

    public boolean isDark() {
        return dark;
    }

    public void setThemeMode(String mode) {
        store.data.theme = mode;
        store.save();
        recreate();
    }

    public void toggleTheme() {
        setThemeMode(dark ? "light" : "dark");
    }

    public void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    public void maybeAskNotifications(boolean force) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        boolean asked = getSharedPreferences("courseflow", MODE_PRIVATE).getBoolean("notif_asked", false);
        if (!asked || force) {
            getSharedPreferences("courseflow", MODE_PRIVATE).edit().putBoolean("notif_asked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    public void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (Exception e) {
            openAppSettings();
        }
    }

    public void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        openAppSettings();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    public void testNotification() {
        if (!AlarmScheduler.canPostNotifications(this)) {
            maybeAskNotifications(true);
            Ui.toast(this, "请先允许通知，再发送测试提醒");
            return;
        }
        AlarmScheduler.showTest(this);
        Ui.toast(this, "已发送测试通知");
    }

    // ------------------------------------------------------------ JSON files

    public void exportTimetable() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "courseflow-课表-" + Dates.stamp() + ".json");
        try {
            startActivityForResult(intent, REQ_EXPORT_TIMETABLE);
        } catch (Exception e) {
            Sheet.alert(this, p, "无法导出", "这台设备没有可用的文件选择器。");
        }
    }

    public void importTimetable() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "text/plain", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQ_IMPORT_TIMETABLE);
        } catch (Exception e) {
            Sheet.alert(this, p, "无法导入", "这台设备没有可用的文件选择器。");
        }
    }

    public void exportNotes() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "courseflow-笔记待办-" + Dates.stamp() + ".json");
        try {
            startActivityForResult(intent, REQ_EXPORT_NOTES);
        } catch (Exception e) {
            Sheet.alert(this, p, "无法导出", "这台设备没有可用的文件选择器。");
        }
    }

    public void importNotes() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "text/plain", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQ_IMPORT_NOTES);
        } catch (Exception e) {
            Sheet.alert(this, p, "无法导入", "这台设备没有可用的文件选择器。");
        }
    }

    public void pickImage(AttachmentSink sink) {
        attachmentSink = sink;
        attachmentPickerActive = true;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_PICK_IMAGE);
    }

    public void captureImage(AttachmentSink sink) {
        try {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    "courseflow_" + System.currentTimeMillis() + ".jpg");
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cameraOutput = getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (cameraOutput == null) {
                Ui.toast(this, "无法创建照片文件");
                return;
            }
            attachmentSink = sink;
            attachmentPickerActive = true;
            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraOutput);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_CAPTURE_IMAGE);
        } catch (Exception e) {
            attachmentPickerActive = false;
            Ui.toast(this, "没有可用的相机应用");
        }
    }

    public void pickFile(AttachmentSink sink) {
        attachmentSink = sink;
        attachmentPickerActive = true;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PICK_FILE);
    }

    public void requestRecordPermission(Runnable granted) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            granted.run();
            return;
        }
        pendingRecordAction = granted;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
    }

    public void importTodoList() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "text/plain", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQ_IMPORT_TODO_LIST);
        } catch (Exception e) {
            Sheet.alert(this, p, "无法导入", "这台设备没有可用的文件选择器。");
        }
    }

    public void importBuiltInTodos() {
        try (java.io.InputStream in = getAssets().open("todo-list-example.json")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            String text = out.toString(StandardCharsets.UTF_8.name());
            confirmTodoImport(LegacyImport.todos(text));
        } catch (Exception e) {
            Sheet.alert(this, p, "导入失败", String.valueOf(e.getMessage()));
        }
    }

    private void confirmTodoImport(java.util.ArrayList<Store.Todo> imported) {
        Sheet.confirm(this, p, "导入 " + imported.size() + " 项待办？",
                "将合并到现有待办；正文中的时间会尽量自动识别为截止提醒。",
                "导入待办", false, () -> {
                    store.data.todos.addAll(imported);
                    store.save();
                    AlarmScheduler.rescheduleAll(this);
                    showTab(TAB_TODOS, true);
                    Ui.toast(this, "待办已导入");
                });
    }

    public void importHolidays() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "text/plain", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQ_IMPORT_HOLIDAYS);
        } catch (Exception e) {
            Sheet.alert(this, p, "无法导入", "这台设备没有可用的文件选择器。");
        }
    }

    public void loadSemesterTimetable() {
        Sheet.confirm(this, p, "载入 2026–2027 第一学期？",
                "会替换当前课程表为内置的 2026–2027 第一学期课表，笔记、待办和私密空间不受影响。",
                "载入课表", false, () -> {
                    store.replaceTimetable(store.builtInSemesterTimetable());
                    scheduleWeekOffset = 0;
                    agendaWeekOffset = 0;
                    showTab(TAB_SCHEDULE, true);
                    Ui.toast(this, "本学期课表已载入");
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        boolean attachmentRequest = requestCode == REQ_PICK_IMAGE
                || requestCode == REQ_CAPTURE_IMAGE || requestCode == REQ_PICK_FILE;
        if (resultCode != RESULT_OK
                || (data == null && requestCode != REQ_CAPTURE_IMAGE)) {
            if (attachmentRequest) {
                attachmentPickerActive = false;
                attachmentSink = null;
                if (requestCode == REQ_CAPTURE_IMAGE && cameraOutput != null) {
                    getContentResolver().delete(cameraOutput, null, null);
                    cameraOutput = null;
                }
            }
            return;
        }
        Uri uri = data == null ? null : data.getData();
        if (requestCode == REQ_CAPTURE_IMAGE) uri = cameraOutput;
        if (uri == null) {
            if (attachmentRequest) {
                attachmentPickerActive = false;
                attachmentSink = null;
            }
            return;
        }
        try {
            if (attachmentRequest) {
                AttachmentSink sink = attachmentSink;
                Store.Attachment attachment = null;
                if (requestCode == REQ_PICK_IMAGE || requestCode == REQ_CAPTURE_IMAGE) {
                    attachment = AttachmentStore.copyImage(this, uri);
                    if (requestCode == REQ_CAPTURE_IMAGE) {
                        getContentResolver().delete(uri, null, null);
                        cameraOutput = null;
                    }
                } else {
                    attachment = AttachmentStore.fileShortcut(this, uri);
                }
                attachmentPickerActive = false;
                attachmentSink = null;
                if (sink != null && attachment != null) sink.onAttachment(attachment);
                return;
            }
            if (requestCode == REQ_EXPORT_TIMETABLE) {
                writeUri(uri, store.timetableJson());
                Ui.toast(this, "课程表已导出");
            } else if (requestCode == REQ_EXPORT_NOTES) {
                writeUri(uri, store.notesJson());
                Ui.toast(this, "笔记与待办已导出");
            } else if (requestCode == REQ_IMPORT_TIMETABLE) {
                String text = readUri(uri);
                try {
                    if (text.contains("\"timetable\"")
                            && (text.contains("星期一") || text.contains("课程代码"))) {
                        pendingTimetable = LegacyImport.timetable(text);
                    } else {
                        pendingTimetable = Store.parseTimetableText(text);
                    }
                } catch (Exception first) {
                    pendingTimetable = LegacyImport.timetable(text);
                }
                confirmTimetableImport();
            } else if (requestCode == REQ_IMPORT_NOTES) {
                String text = readUri(uri);
                pendingNotes = Store.parseNotesText(text);
                confirmNotesImport();
            } else if (requestCode == REQ_IMPORT_HOLIDAYS) {
                String text = readUri(uri);
                java.util.ArrayList<Store.Holiday> holidays = Store.parseHolidayText(text);
                for (Store.Holiday imported : holidays) {
                    store.data.holidays.removeIf(h -> h.date.equals(imported.date));
                    store.data.holidays.add(imported);
                }
                store.save();
                Ui.toast(this, "已导入 " + holidays.size() + " 个节假日 / 调休日");
                refresh();
            } else if (requestCode == REQ_IMPORT_TODO_LIST) {
                String text = readUri(uri);
                confirmTodoImport(LegacyImport.todos(text));
            }
        } catch (IllegalArgumentException e) {
            Sheet.alert(this, p, "JSON 格式有误", e.getMessage() == null ? "无法解析这个文件。" : e.getMessage());
        } catch (JSONException e) {
            Sheet.alert(this, p, "JSON 格式有误", "无法解析这个文件，请确认它是有效的 JSON。\n\n" + e.getMessage());
        } catch (Exception e) {
            Sheet.alert(this, p, "操作失败", String.valueOf(e.getMessage()));
        } finally {
            if (attachmentRequest) {
                attachmentPickerActive = false;
                attachmentSink = null;
            }
        }
    }

    private void confirmTimetableImport() {
        if (pendingTimetable == null) return;
        Store.Timetable t = pendingTimetable;
        StringBuilder msg = new StringBuilder();
        msg.append("学期：").append(t.name).append("\n");
        msg.append("第一周：").append(t.termStart).append("（周一）\n");
        msg.append("时间段：").append(t.timeSlots.size()).append(" 个\n");
        msg.append("课程：").append(t.courses.size()).append(" 门\n\n");
        msg.append("导入会替换当前课程表，笔记与待办不受影响。");
        Sheet.confirm(this, p, "导入这份课程表？", msg.toString(), "替换并导入", false, () -> {
            store.replaceTimetable(t);
            pendingTimetable = null;
            scheduleWeekOffset = 0;
            agendaWeekOffset = 0;
            Ui.toast(this, "课程表已导入");
            showTab(TAB_SCHEDULE, true);
        });
    }

    private void confirmNotesImport() {
        if (pendingNotes == null) return;
        Store.NotesBundle b = pendingNotes;
        Sheet sheet = new Sheet(this, p, "导入笔记与待办", "选择一种合并方式");
        String info = "笔记 " + b.notes.size() + " 条   ·   待办 " + b.todos.size() + " 条";
        TextView line = Ui.text(this, info, 14, p.ink, true);
        sheet.add(line);

        TextView detail = Ui.text(this,
                b.vault != null
                        ? "文件还包含加密的私密笔记。只有在当前私密空间为空或密码相同时才会一并导入；密码不会被写入文件。"
                        : "导入不会改动课程表。",
                12.5f, p.faint, false);
        detail.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        sheet.add(detail);

        TextView merge = Ui.primaryButton(this, "合并到现有内容", p);
        merge.setOnClickListener(v -> {
            Store.ImportResult r = store.applyNotesBundle(b, false);
            pendingNotes = null;
            sheet.dismiss();
            AlarmScheduler.rescheduleAll(this);
            refresh();
            importToast(r);
        });
        TextView replace = Ui.secondaryButton(this, "替换现有笔记与待办", p);
        replace.setOnClickListener(v -> {
            Sheet.confirm(this, p, "确认替换？", "现有的全部普通笔记和待办会被导入文件替换，此操作无法撤销。",
                    "替换", true, () -> {
                        Store.ImportResult r = store.applyNotesBundle(b, true);
                        pendingNotes = null;
                        AlarmScheduler.rescheduleAll(this);
                        refresh();
                        importToast(r);
                    });
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        sheet.addFooter(merge);
        sheet.addFooter(replace, lp);
        TextView cancel = Ui.textButton(this, "取消", p.sub, p);
        cancel.setOnClickListener(v -> sheet.dismiss());
        sheet.addFooter(cancel);
        sheet.show();
    }

    private void importToast(Store.ImportResult r) {
        StringBuilder msg = new StringBuilder("已导入 " + r.notes + " 条笔记、" + r.todos + " 条待办");
        if (r.privateSkipped) {
            msg.append("；私密笔记因密码不同未导入");
        } else if (r.privateImported) {
            msg.append("；私密笔记已加密导入");
        }
        Ui.toast(this, msg.toString());
    }

    private void writeUri(Uri uri, String text) throws Exception {
        OutputStream out = null;
        try {
            try {
                out = getContentResolver().openOutputStream(uri, "wt");
            } catch (Exception ignored) {
                out = getContentResolver().openOutputStream(uri, "w");
            }
            if (out == null) throw new Exception("无法写入所选文件");
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } finally {
            if (out != null) out.close();
        }
    }

    private String readUri(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("无法读取所选文件");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > 8 * 1024 * 1024) throw new Exception("文件过大（上限 8 MB）");
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingRecordAction != null) pendingRecordAction.run();
            else if (!granted) Ui.toast(this, "未获得麦克风权限，无法录音");
            pendingRecordAction = null;
            return;
        }
        if (requestCode == REQ_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Ui.toast(this, "通知已开启");
                AlarmScheduler.rescheduleAll(this);
            } else {
                Ui.toast(this, "未开启通知，待办仍会保存但不会弹窗提醒");
            }
            if (tab == TAB_TODOS || tab == TAB_ALARM || tab == TAB_RECORDINGS
                    || tab == TAB_SETTINGS) refresh();
        }
    }

    @Override
    public void onBackPressed() {
        if (screen != null && screen.onBack()) return;
        if (tab != TAB_SCHEDULE) {
            showTab(TAB_SCHEDULE, true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String requested = intent == null ? null : intent.getStringExtra(AlarmScheduler.EXTRA_TAB);
        if ("todos".equals(requested)) showTab(TAB_TODOS, true);
        else if ("notes".equals(requested)) showTab(TAB_NOTES, true);
        else if ("alarm".equals(requested)) showTab(TAB_ALARM, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmScheduler.rescheduleAll(this);
        ClockAlarmScheduler.scheduleAll(this);
        if (tab == TAB_NOTES && notesScreen != null) notesScreen.onActivityResume();
        if (tab == TAB_SETTINGS && screen != null) showTab(TAB_SETTINGS, false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!attachmentPickerActive && notesScreen != null) notesScreen.lockNow();
    }

    private static class NavItem {
        final View root;
        final FrameLayout holder;
        final ImageView icon;
        final TextView label;
        final int index;

        NavItem(View root, FrameLayout holder, ImageView icon, TextView label, int index) {
            this.root = root;
            this.holder = holder;
            this.icon = icon;
            this.label = label;
            this.index = index;
        }
    }
}
