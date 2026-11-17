package ba.infotablet;

import android.app.Application;

import ba.infotablet.net.Net;

public final class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Net.init(this);
    }
}
