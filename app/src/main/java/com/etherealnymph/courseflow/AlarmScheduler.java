package com.etherealnymph.courseflow;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

public final class AlarmScheduler {

    public static final String CHANNEL_ID = "courseflow_todos";
    public static final String CHANNEL_ALARM = "courseflow_todo_alarm";
    public static final String ACTION_ALARM = "courseflow.ACTION_TODO_ALARM";
    public static final String ACTION_DONE = "courseflow.ACTION_TODO_DONE";
    public static final String EXTRA_ID = "todo_id";
    public static final String EXTRA_TAB = "open_tab";
    public static final String EXTRA_KIND = "reminder_kind";
    public static final String KIND_START = "start";
    public static final String KIND_DUE = "due";
    public static final String KIND_ADVANCE = "advance";

    private AlarmScheduler() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "待办提醒",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("待办到达设定时间时提醒");
        channel.enableLights(true);
        channel.setLightColor(0xFF0F7A64);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 180, 90, 180});
        nm.createNotificationChannel(channel);

        NotificationChannel alarm = new NotificationChannel(CHANNEL_ALARM, "待办闹铃",
                NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription("待办提前提醒选择响铃时使用");
        alarm.enableVibration(true);
        alarm.setVibrationPattern(new long[]{0, 500, 300, 500});
        alarm.setSound(android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_ALARM),
                new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        nm.createNotificationChannel(alarm);
    }

    public static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm == null || nm.areNotificationsEnabled();
    }

    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    public static void schedule(Context context, Store.Todo todo) {
        if (todo == null || todo.done) {
            if (todo != null) cancel(context, todo);
            return;
        }
        scheduleOne(context, todo, KIND_START, todo.remindAt);
        scheduleOne(context, todo, KIND_DUE, todo.dueAt);
        long advanceAt = 0;
        if (todo.dueAt > 0 && todo.advanceMinutes > 0) {
            advanceAt = todo.dueAt - todo.advanceMinutes * 60_000L;
        }
        scheduleOne(context, todo, KIND_ADVANCE, advanceAt);
    }

    private static void scheduleOne(Context context, Store.Todo todo, String kind, long at) {
        if (at <= 0) {
            cancelAlarm(context, todo, kind);
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(requestCode(todo.id + kind));
            return;
        }
        // Never fire a backlog of overdue reminders when the app resumes. Keep
        // any notification that has already been posted; only drop the alarm.
        if (at <= System.currentTimeMillis()) {
            cancelAlarm(context, todo, kind);
            return;
        }
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = alarmIntent(context, todo.id, kind);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    public static void cancel(Context context, Store.Todo todo) {
        if (todo == null) return;
        cancelAlarm(context, todo, KIND_START);
        cancelAlarm(context, todo, KIND_DUE);
        cancelAlarm(context, todo, KIND_ADVANCE);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(requestCode(todo.id + KIND_START));
            nm.cancel(requestCode(todo.id + KIND_DUE));
            nm.cancel(requestCode(todo.id + KIND_ADVANCE));
        }
    }

    private static void cancelAlarm(Context context, Store.Todo todo, String kind) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarmIntent(context, todo.id, kind));
    }

    public static void rescheduleAll(Context context) {
        ensureChannel(context);
        Store store = Store.get(context);
        for (Store.Todo todo : store.data.todos) {
            if (todo.done) continue;
            schedule(context, todo);
        }
    }

    public static void showNotification(Context context, Store.Todo todo) {
        showNotification(context, todo, KIND_DUE);
    }

    public static void showNotification(Context context, Store.Todo todo, String kind) {
        if (todo == null) return;
        if (!canPostNotifications(context)) return;
        ensureChannel(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent open = new Intent(context, MainActivity.class);
        open.putExtra(EXTRA_TAB, MainActivity.TAB_TODOS);
        open.putExtra(EXTRA_ID, todo.id);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, requestCode(todo.id + "open"), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent done = new Intent(context, TodoAlarmReceiver.class);
        done.setAction(ACTION_DONE);
        done.putExtra(EXTRA_ID, todo.id);
        PendingIntent complete = PendingIntent.getBroadcast(context, requestCode(todo.id + "done"), done,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        boolean advance = KIND_ADVANCE.equals(kind);
        String body = todo.note == null || todo.note.trim().isEmpty()
                ? (advance ? "距离截止还有一段时间，提前准备一下。" : "时间到了，记得完成这件事。")
                : todo.note.trim();
        boolean start = KIND_START.equals(kind);
        String title = (advance ? "提前提醒 · " : start ? "开始提醒 · " : "截止提醒 · ") + todo.title;
        String channel = advance && todo.advanceAlarm ? CHANNEL_ALARM : CHANNEL_ID;
        Notification.Builder builder = new Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setColor(0xFF0F7A64)
                .setAutoCancel(true)
                .setCategory(advance && todo.advanceAlarm
                        ? Notification.CATEGORY_ALARM : Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(content)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_check_circle),
                        "标记完成", complete).build());
        long at = advance ? Math.max(0, todo.dueAt - todo.advanceMinutes * 60_000L)
                : start ? todo.remindAt : todo.dueAt;
        if (at > 0) {
            String label = advance
                    ? "提前 " + todo.advanceMinutes + " 分钟 · 截止 " + Dates.dueLabel(todo.dueAt)
                    : (start ? "开始 " : "截止 ") + Dates.dueLabel(at);
            builder.setSubText(label);
        }
        nm.notify(requestCode(todo.id + kind), builder.build());
    }

    public static void showTest(Context context) {
        Store.Todo t = new Store.Todo();
        t.id = "test";
        t.title = "时屿 · 提醒测试";
        t.note = "通知工作正常。你的待办会在设定时间出现在这里。";
        t.dueAt = System.currentTimeMillis();
        showNotification(context, t);
    }

    private static PendingIntent alarmIntent(Context context, String todoId, String kind) {
        Intent intent = new Intent(context, TodoAlarmReceiver.class);
        intent.setAction(ACTION_ALARM);
        intent.putExtra(EXTRA_ID, todoId);
        intent.putExtra(EXTRA_KIND, kind);
        return PendingIntent.getBroadcast(context, requestCode(todoId + kind), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int requestCode(String id) {
        if (id == null) return 1;
        int h = id.hashCode();
        return (h & 0x7FFFFFFF) % 2_000_000_000 + 1;
    }
}
