package com.etherealnymph.courseflow;

import android.app.Application;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Store.get(this);
        AlarmScheduler.ensureChannel(this);
        AlarmScheduler.rescheduleAll(this);
        ClockAlarmScheduler.scheduleAll(this);
    }
}
