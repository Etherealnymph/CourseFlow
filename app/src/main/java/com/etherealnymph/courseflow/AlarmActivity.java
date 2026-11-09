package com.etherealnymph.courseflow;

import android.app.Activity;
import android.app.NotificationManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Full-screen alarm UI shown over the lock screen. */
public class AlarmActivity extends Activity {
    private String alarmId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        alarmId = getIntent() == null ? "" : getIntent().getStringExtra(ClockAlarmScheduler.EXTRA_ID);
        Store store = Store.get(this);
        Store.Alarm alarm = null;
        for (Store.Alarm a : store.data.alarms) {
            if (a.id.equals(alarmId)) {
                alarm = a;
                break;
            }
        }
        Palette p = new Palette(resolveDark(store.data.theme) == 1);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(p.bg);
        root.setPadding(Ui.dp(this, 28), Ui.dp(this, 40), Ui.dp(this, 28), Ui.dp(this, 40));

        TextView eyebrow = Ui.eyebrow(this, "ALARM · 闹钟", p);
        eyebrow.setGravity(Gravity.CENTER);
        root.addView(eyebrow);

        TextView time = Ui.text(this,
                alarm == null ? "--:--" : String.format(Locale.CHINA, "%02d:%02d", alarm.hour, alarm.minute),
                64, p.ink, true);
        time.setGravity(Gravity.CENTER);
        time.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
        root.addView(time);

        TextView label = Ui.text(this,
                alarm == null || alarm.label.isEmpty() ? "闹钟响了" : alarm.label,
                22, p.primary, true);
        label.setGravity(Gravity.CENTER);
        root.addView(label);

        TextView hint = Ui.text(this, "关闭闹钟会同时停止铃声和震动", 12, p.faint, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 36));
        root.addView(hint);

        TextView stop = Ui.primaryButton(this, "关闭闹钟", p);
        stop.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        stop.setOnClickListener(v -> dismissAlarm());
        root.addView(stop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));
        setContentView(root);
    }

    private int resolveDark(String theme) {
        if ("dark".equals(theme)) return 1;
        if ("light".equals(theme)) return 0;
        return (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES ? 1 : 0;
    }

    private void dismissAlarm() {
        AlarmRingService.stop(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && !alarmId.isEmpty()) nm.cancel(ClockAlarmScheduler.code(alarmId));
        finish();
    }

    @Override
    public void onBackPressed() {
        dismissAlarm();
    }
}
