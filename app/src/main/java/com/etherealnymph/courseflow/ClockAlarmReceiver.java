package com.etherealnymph.courseflow;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ClockAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra(ClockAlarmScheduler.EXTRA_ID);
        if (id == null) return;
        Store store = Store.get(context);
        Store.Alarm alarm = null;
        for (Store.Alarm a : store.data.alarms) {
            if (a.id.equals(id)) {
                alarm = a;
                break;
            }
        }
        if (alarm == null) return;
        if (ClockAlarmScheduler.ACTION_DISMISS.equals(intent.getAction())) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(ClockAlarmScheduler.code(id));
            AlarmRingService.stop(context);
            return;
        }
        if (ClockAlarmScheduler.ACTION_TRIGGER.equals(intent.getAction())) {
            int index = intent.getIntExtra(ClockAlarmScheduler.EXTRA_REPEAT_INDEX, 0);
            ClockAlarmScheduler.show(context, alarm);
            if (index + 1 < Math.max(1, alarm.repeatCount)) {
                ClockAlarmScheduler.scheduleRepeat(context, alarm, index + 1);
            } else {
                ClockAlarmScheduler.schedule(context, alarm);
            }
        }
    }
}
