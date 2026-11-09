package com.etherealnymph.courseflow;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Plays the actual alarm ringtone for the configured duration. */
public class AlarmRingService extends Service {
    public static final String ACTION_STOP = "courseflow.ACTION_RING_STOP";
    public static final String EXTRA_ID = "ring_alarm_id";

    private Ringtone ringtone;
    private MediaPlayer player;
    private ToneGenerator tone;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int notifyId;

    public static void start(Context context, Store.Alarm alarm) {
        if (alarm == null) return;
        ClockAlarmScheduler.ensureChannel(context);
        Intent intent = new Intent(context, AlarmRingService.class);
        intent.putExtra(EXTRA_ID, alarm.id);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            fallback(context, alarm);
        }
    }

    private static void fallback(Context context, Store.Alarm alarm) {
        try {
            Intent dismiss = new Intent(context, ClockAlarmReceiver.class);
            dismiss.setAction(ClockAlarmScheduler.ACTION_DISMISS);
            dismiss.putExtra(ClockAlarmScheduler.EXTRA_ID, alarm.id);
            PendingIntent stop = PendingIntent.getBroadcast(context,
                    ClockAlarmScheduler.code(alarm.id + "fallback"), dismiss,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = ClockAlarmScheduler.notification(context, alarm, stop,
                    ClockAlarmScheduler.CHANNEL_ID);
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(ClockAlarmScheduler.code(alarm.id), n);
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, AlarmRingService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            finish();
            return START_NOT_STICKY;
        }
        String id = intent == null ? null : intent.getStringExtra(EXTRA_ID);
        Store.Alarm alarm = null;
        if (id != null) {
            for (Store.Alarm a : Store.get(this).data.alarms) {
                if (a.id.equals(id)) {
                    alarm = a;
                    break;
                }
            }
        }
        if (alarm == null) {
            finish();
            return START_NOT_STICKY;
        }

        if (ringtone != null) {
            try {
                if (ringtone.isPlaying()) ringtone.stop();
            } catch (Exception ignored) {
            }
        }
        if (player != null) {
            try {
                player.stop();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {
            }
        }
        ClockAlarmScheduler.ensureChannel(this);
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "courseflow:alarm");
                wakeLock.acquire(Math.max(5, Math.min(120, alarm.ringSeconds)) * 1000L + 15000L);
            }
        } catch (Exception ignored) {
        }
        notifyId = ClockAlarmScheduler.code(alarm.id);
        Intent stop = new Intent(this, AlarmRingService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this,
                ClockAlarmScheduler.code(alarm.id + "ringstop"), stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = ClockAlarmScheduler.notification(this, alarm, stopPi);
        try {
            startForeground(notifyId, notification);
        } catch (Exception e) {
            finish();
            fallback(this, alarm);
            return START_NOT_STICKY;
        }

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes alarmAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(alarmAttrs);
            player.setDataSource(this, sound);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            if (player != null) {
                try {
                    player.release();
                } catch (Exception ignored) {
                }
                player = null;
            }
            try {
                ringtone = RingtoneManager.getRingtone(this, sound);
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ringtone.setAudioAttributes(alarmAttrs);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone.setLooping(true);
                    ringtone.play();
                }
            } catch (Exception ignored) {
            }
        }
        if (player == null && ringtone == null) {
            try {
                tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500);
            } catch (Exception ignored) {
            }
        }

        try {
            if (alarm.vibrate) {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    long[] pattern = {0, 500, 300, 500};
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        handler.removeCallbacksAndMessages(null);
        int seconds = Math.max(5, Math.min(120, alarm.ringSeconds));
        handler.postDelayed(this::finish, seconds * 1000L);
        return START_NOT_STICKY;
    }

    private void finish() {
        handler.removeCallbacksAndMessages(null);
        try {
            if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        } catch (Exception ignored) {
        }
        ringtone = null;
        try {
            if (player != null) {
                if (player.isPlaying()) player.stop();
                player.release();
            }
        } catch (Exception ignored) {
        }
        player = null;
        try {
            if (tone != null) {
                tone.stopTone();
                tone.release();
            }
        } catch (Exception ignored) {
        }
        tone = null;
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
        wakeLock = null;
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) {
        }
        vibrator = null;
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(notifyId);
        } catch (Exception ignored) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        finish();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
