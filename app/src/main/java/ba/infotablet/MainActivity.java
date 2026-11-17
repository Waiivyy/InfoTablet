package ba.infotablet;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.Locale;

import ba.infotablet.fuel.FuelScreen;
import ba.infotablet.home.HomeScreen;
import ba.infotablet.home.PlaceholderPanel;
import ba.infotablet.news.NewsScreen;
import ba.infotablet.sync.Scheduler;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Rail;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.StatusLine;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Clock;
import ba.infotablet.util.Fmt;
import ba.infotablet.weather.WeatherScreen;

/**
 * The only activity. It is also the device's home screen, so it never finishes: Back and Home
 * lead to Početna, and two minutes without touch return there as well.
 */
public final class MainActivity extends Activity implements Rail.Listener, Scheduler.Listener {

    private static final long IDLE_RETURN_MS = 2 * 60 * 1000L;
    private static final long LOW_PROFILE_DELAY_MS = 3000L;

    private final Handler handler = new Handler();
    private final Runnable idleReturn = () -> onTab(0);
    private final Runnable lowProfile = this::applyLowProfile;

    private Palette palette = Palette.LIGHT;
    private Rail rail;
    private FrameLayout content;
    private StatusLine status;
    private Refreshable screen;
    private int tab = -1;

    private int batteryTenthsC = Integer.MIN_VALUE;
    private boolean wifiOk;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            refreshScreen();
            refreshStatus();
        }
    };

    private final BroadcastReceiver systemReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
                batteryTenthsC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            } else {
                wifiOk = isWifiConnected();
            }
            refreshStatus();
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Fonts.load(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == 0) {
                handler.removeCallbacks(lowProfile);
                handler.postDelayed(lowProfile, LOW_PROFILE_DELAY_MS);
            }
        });
        buildUi();
        onTab(0);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(palette.bg);

        rail = new Rail(this, palette, this);
        root.addView(rail, new LinearLayout.LayoutParams(Ui.dp(this, 92), LinearLayout.LayoutParams.MATCH_PARENT));
        root.addView(Ui.hairline(this, palette.line), new LinearLayout.LayoutParams(Math.max(1, Ui.dp(this, 1)), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        content = new FrameLayout(this);
        column.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        column.addView(Ui.hairline(this, palette.line), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));
        status = new StatusLine(this, palette);
        column.addView(status, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 23)));
        root.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        setContentView(root);
    }

    @Override
    public void onTab(int index) {
        if (index != tab) {
            tab = index;
            rail.select(index);
            content.removeAllViews();
            View view;
            if (index == 0) {
                HomeScreen h = new HomeScreen(this, palette);
                screen = h;
                view = h;
            } else if (index == 1) {
                WeatherScreen w = new WeatherScreen(this, palette);
                screen = w;
                view = w;
            } else if (index == 2) {
                FuelScreen f = new FuelScreen(this, palette);
                screen = f;
                view = f;
            } else if (index == 4) {
                NewsScreen n = new NewsScreen(this, palette);
                screen = n;
                view = n;
            } else {
                screen = null;
                FrameLayout wrap = new FrameLayout(this);
                int pad = Ui.dp(this, 12);
                wrap.setPadding(pad, pad, pad, pad);
                wrap.addView(new PlaceholderPanel(this, palette, Rail.LABELS[index], "Ovaj ekran dolazi uskoro"));
                view = wrap;
            }
            content.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            refreshScreen();
        }
        restartIdleTimer();
    }

    @Override
    public void onDataChanged() {
        refreshScreen();
        refreshStatus();
    }

    private void refreshScreen() {
        if (screen != null) screen.refresh(Clock.now());
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        time.addAction(Intent.ACTION_DATE_CHANGED);
        registerReceiver(timeReceiver, time);

        IntentFilter sys = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        sys.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(systemReceiver, sys);

        Scheduler.get(this).addListener(this);
        wifiOk = isWifiConnected();
        refreshScreen();
        refreshStatus();
        applyLowProfile();
        restartIdleTimer();
    }

    @Override
    protected void onPause() {
        Scheduler.get(this).removeListener(this);
        unregisterReceiver(timeReceiver);
        unregisterReceiver(systemReceiver);
        handler.removeCallbacks(idleReturn);
        handler.removeCallbacks(lowProfile);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        onTab(0); // Home button pressed while already in front.
    }

    // The target device runs Android 4.2 with a Back button and no back gestures.
    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        onTab(0); // A home screen never closes.
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) restartIdleTimer();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyLowProfile();
    }

    private void restartIdleTimer() {
        handler.removeCallbacks(idleReturn);
        if (tab != 0) handler.postDelayed(idleReturn, IDLE_RETURN_MS);
    }

    private void applyLowProfile() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected() && ni.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private void refreshStatus() {
        Scheduler.Summary s = Scheduler.get(this).summary();
        String sync = s.lastOkAt == 0 ? "--:--" : Fmt.hhmm(s.lastOkAt);
        int level = (!wifiOk || s.ok < s.total) ? 1 : 0;
        status.setLeft("Sync " + sync + " · " + s.ok + "/" + s.total + " izvora OK · WiFi " + (wifiOk ? "OK" : "nema"), level);
        String battery = batteryTenthsC == Integer.MIN_VALUE ? "--" : String.format(Locale.ROOT, "%.0f", batteryTenthsC / 10f);
        status.setRight("Baterija " + battery + " °C");
    }
}
