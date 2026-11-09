package com.etherealnymph.courseflow;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TodoAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra(AlarmScheduler.EXTRA_ID);
        if (id == null) return;
        Store store = Store.get(context);
        Store.Todo todo = null;
        for (Store.Todo t : store.data.todos) {
            if (t.id.equals(id)) {
                todo = t;
                break;
            }
        }
        if (todo == null) return;

        if (AlarmScheduler.ACTION_DONE.equals(intent.getAction())) {
            todo.done = true;
            todo.doneAt = System.currentTimeMillis();
            store.save();
            AlarmScheduler.cancel(context, todo);
            return;
        }

        if (AlarmScheduler.ACTION_ALARM.equals(intent.getAction()) && !todo.done) {
            String kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND);
            if (kind == null) kind = AlarmScheduler.KIND_DUE;
            AlarmScheduler.showNotification(context, todo, kind);
        }
    }
}
