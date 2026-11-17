package ba.infotablet;

import android.app.Application;

import ba.infotablet.net.Net;
import ba.infotablet.sync.Scheduler;

public final class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Net.init(this);
        Scheduler.get(this).start();
    }
}
