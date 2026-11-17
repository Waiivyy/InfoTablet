package ba.infotablet;

import android.app.Application;

import ba.infotablet.alarm.AlarmScheduler;
import ba.infotablet.data.HealthLog;
import ba.infotablet.net.Net;
import ba.infotablet.sync.Scheduler;
import ba.infotablet.update.Updater;
import ba.infotablet.web.WebServer;

public final class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        HealthLog.start(this);
        Net.init(this);
        Updater.settle(this);
        Scheduler.get(this).start();
        AlarmScheduler.reschedule(this);
        WebServer.update(this);
    }
}
