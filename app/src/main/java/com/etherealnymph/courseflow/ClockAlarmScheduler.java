package com.etherealnymph.courseflow;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import java.time.LocalDate;

public final class ClockAlarmScheduler {

    public static final String CHANNEL_ID = "courseflow_alarms";
    public static final String CHANNEL_SILENT = "courseflow_alarms_ringing";
    public static final String ACTION_TRIGGER = "courseflow.ACTION_CLOCK";
    public static final String ACTION_DISMISS = "courseflow.ACTION_CLOCK_DISMISS";
    public static final String EXTRA_ID = "alarm_id";
    public static final String EXTRA_REPEAT_INDEX = "alarm_repeat_index";
    public static final String EXTRA_TAB_ALARM = "alarm";

    private ClockAlarmScheduler() {}

    public static void ensureChannel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "闹钟",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("工作日、非工作日与自定义闹钟");
        // Ringing/vibration is controlled by AlarmRingService; the fallback
        // notification should not add an unexpected vibration.
        channel.enableVibration(false);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(sound, attrs);
        nm.createNotificationChannel(channel);

        NotificationChannel silent = new NotificationChannel(CHANNEL_SILENT, "闹钟响铃",
                NotificationManager.IMPORTANCE_LOW);
        silent.setDescription("响铃由闹钟服务播放");
        silent.setSound(null, null);
        silent.enableVibration(false);
        nm.createNotificationChannel(silent);
    }

    public static void scheduleAll(Context context) {
        ensureChannel(context);
        Store store = Store.get(context);
        for (Store.Alarm alarm : store.data.alarms) schedule(context, alarm);
    }

    public static void schedule(Context context, Store.Alarm alarm) {
        if (alarm == null) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < 12; i++) am.cancel(pending(context, alarm.id, i));
        if (!alarm.enabled) {
            cancel(context, alarm);
            return;
        }
        long at = nextTrigger(context, alarm);
        if (at <= 0) return;
        scheduleAt(context, alarm, 0, at);
    }

    public static void scheduleRepeat(Context context, Store.Alarm alarm, int index) {
        if (alarm == null || !alarm.enabled) return;
        if (index >= Math.max(1, alarm.repeatCount)) {
            schedule(context, alarm);
            return;
        }
        long at = System.currentTimeMillis()
                + Math.max(1, alarm.repeatIntervalMin) * 60_000L;
        scheduleAt(context, alarm, index, at);
    }

    private static void scheduleAt(Context context, Store.Alarm alarm, int index, long at) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null || at <= 0) return;
        PendingIntent pi = pending(context, alarm.id, index);
        Intent show = new Intent(context, AlarmActivity.class);
        show.putExtra(EXTRA_ID, alarm.id);
        show.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent showPi = PendingIntent.getActivity(context,
                code(alarm.id + "show"), show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            // AlarmClockInfo is the system's dedicated wake-up alarm path: it
            // wakes the device and is exact even in Doze.
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showPi), pi);
            return;
        } catch (Exception ignored) {
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception e) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Exception ignored) {
            }
        }
    }

    public static void cancel(Context context, Store.Alarm alarm) {
        if (alarm == null) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            for (int i = 0; i < 12; i++) am.cancel(pending(context, alarm.id, i));
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(code(alarm.id));
        AlarmRingService.stop(context);
    }

    public static long nextTrigger(Context context, Store.Alarm alarm) {
        Store store = Store.get(context);
        LocalDate today = Dates.today();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 370; i++) {
            LocalDate date = today.plusDays(i);
            if (!allowed(store, alarm, date)) continue;
            long at = Dates.at(date, alarm.hour, alarm.minute);
            if (at > now + 1000) return at;
        }
        return 0;
    }

    public static boolean allowed(Store store, Store.Alarm alarm, LocalDate date) {
        String key = Dates.date(date);
        if (alarm.startDate != null && !alarm.startDate.isEmpty()) {
            LocalDate start = Dates.parseDate(alarm.startDate);
            if (start != null && date.isBefore(start)) return false;
        }
        if (alarm.endDate != null && !alarm.endDate.isEmpty()) {
            LocalDate end = Dates.parseDate(alarm.endDate);
            if (end != null && date.isAfter(end)) return false;
        }
        if ("workday_plus".equals(alarm.mode)) {
            if (store.isWorkday(date)) return true;
            return alarm.extraDates != null && alarm.extraDates.contains(key);
        }
        if ("custom".equals(alarm.mode) && alarm.extraDates != null
                && alarm.extraDates.contains(key)) return true;
        if ("workday".equals(alarm.mode)) return store.isWorkday(date);
        if ("offday".equals(alarm.mode)) return !store.isWorkday(date);
        int bit = 1 << (Dates.dow(date) - 1);
        return (alarm.weekdays & bit) != 0;
    }

    public static String modeLabel(Store.Alarm alarm) {
        if ("workday".equals(alarm.mode)) return "工作日（排除节假日）";
        if ("offday".equals(alarm.mode)) return "非工作日（含节假日）";
        if ("workday_plus".equals(alarm.mode)) return "工作日 + 自定义日期";
        StringBuilder sb = new StringBuilder("自定义 · ");
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < 7; i++) {
            if ((alarm.weekdays & (1 << i)) != 0) sb.append(names[i]);
        }
        if (alarm.extraDates != null && !alarm.extraDates.isEmpty()) {
            sb.append(" + ").append(alarm.extraDates.size()).append(" 个日期");
        }
        return sb.toString();
    }

    public static void show(Context context, Store.Alarm alarm) {
        AlarmRingService.start(context, alarm);
    }

    public static Notification notification(Context context, Store.Alarm alarm,
                                             PendingIntent stop) {
        return notification(context, alarm, stop, CHANNEL_SILENT);
    }

    public static Notification notification(Context context, Store.Alarm alarm,
                                             PendingIntent stop, String channel) {
        Intent open = new Intent(context, AlarmActivity.class);
        open.putExtra(EXTRA_ID, alarm.id);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, code(alarm.id + "open"), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("闹钟 · " + (alarm.label.isEmpty() ? "该起床了" : alarm.label))
                .setContentText(String.format(java.util.Locale.CHINA,
                        "现在 %02d:%02d · 响铃 %d 秒", alarm.hour, alarm.minute, alarm.ringSeconds))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(content)
                .setFullScreenIntent(content, true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_check_circle),
                        "知道了", stop).build())
                .build();
    }

    private static PendingIntent pending(Context context, String id, int index) {
        Intent intent = new Intent(context, ClockAlarmReceiver.class);
        intent.setAction(ACTION_TRIGGER);
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_REPEAT_INDEX, index);
        return PendingIntent.getBroadcast(context, code(id + "#" + index), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static int code(String id) {
        int h = id == null ? 7 : id.hashCode();
        return (h & 0x7FFFFFFF) % 1_900_000_000 + 1;
    }
}
