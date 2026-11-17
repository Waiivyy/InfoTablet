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

import ba.infotablet.alarm.AlarmScheduler;
import ba.infotablet.calendar.CalendarScreen;
import ba.infotablet.fuel.FuelScreen;
import ba.infotablet.home.HomeScreen;
import ba.infotablet.news.NewsScreen;
import ba.infotablet.reminders.RemindersScreen;
import ba.infotablet.settings.SettingsScreen;
import ba.infotablet.sport.SportScreen;
import ba.infotablet.sync.Scheduler;
import ba.infotablet.ui.Busy;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.GridDrawable;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Rail;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.StatusLine;
import ba.infotablet.ui.Theme;
import ba.infotablet.ui.Ui;
import ba.infotablet.useful.UsefulScreen;
import ba.infotablet.util.Clock;
import ba.infotablet.util.Fmt;
import ba.infotablet.weather.WeatherScreen;

/**
 * The only activity. It is also the device's home screen, so it never finishes: Back and Home
 * lead to Početna, and two minutes without touch return there as well. It switches between the
 * light and dark palette at sunrise and sunset and dims the screen at night (never off).
 */
public final class MainActivity extends Activity implements Rail.Listener, Scheduler.Listener, SettingsScreen.Host {

    private static final long IDLE_RETURN_MS = 2 * 60 * 1000L;
    /** With unsaved input (an open reminder editor) the return waits up to this long. */
    private static final long IDLE_RETURN_BUSY_MS = 10 * 60 * 1000L;
    private static final long LOW_PROFILE_DELAY_MS = 3000L;
    /** A touch at night brightens the screen for this long. */
    private static final long NIGHT_TOUCH_MS = 60_000L;
    private static final int SETTINGS_TAB = 8;

    private final Handler handler = new Handler();
    private final Runnable idleReturn = this::onIdle;
    private final Runnable lowProfile = this::applyLowProfile;
    private final Runnable applyBrightness = this::applyBrightness;
    private long lastTouchAt;
    private long brightUntil;
    private long previewUntil;

    private Palette palette = Palette.LIGHT;
    private String lookKey = "";
    private Rail rail;
    private FrameLayout content;
    private StatusLine status;
    private Refreshable screen;
    private int tab = -1;

    private int batteryTenthsC = Integer.MIN_VALUE;
    private boolean wifiOk;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            // A sunrise or sunset switch waits while someone is typing (it would rebuild the editor).
            boolean busy = screen instanceof Busy && ((Busy) screen).busy();
            if (busy || !applyLook()) refreshScreen();
            refreshStatus();
            applyBrightness();
            AlarmScheduler.checkRinging(c); // safety net if a system alarm was missed
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
        applyLook();
        onTab(0);
    }

    /**
     * Rebuilds every view when the palette or a look setting changed (sunset, sunrise, Postavke).
     * Returns true when it rebuilt.
     */
    private boolean applyLook() {
        long now = Clock.now();
        String key = Theme.lookKey(this, now);
        if (key.equals(lookKey)) return false;
        lookKey = key;
        Theme.configureUi(this);
        palette = Theme.palette(this, now);
        int keep = tab < 0 ? 0 : tab;
        buildUi();
        tab = -1;
        onTab(Settings.of(this).tabVisible(keep) ? keep : 0);
        return true;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        if (palette.grid) {
            root.setBackgroundDrawable(new GridDrawable(palette.bg, palette.dark ? 0xFF121920 : 0xFFDCE1E6, Ui.dp(this, 24)));
        } else {
            root.setBackgroundColor(palette.bg);
        }

        boolean[] visible = new boolean[Rail.LABELS.length];
        Settings s = Settings.of(this);
        for (int i = 0; i < visible.length; i++) visible[i] = s.tabVisible(i);
        rail = new Rail(this, palette, this, visible);
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
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(palette.bg));
        refreshStatus();
    }

    @Override
    public void onTab(int index) {
        if (index != tab) {
            if (tab == SETTINGS_TAB) SettingsScreen.lock(); // leaving Postavke locks them again
            tab = index;
            rail.select(index);
            content.removeAllViews();
            View view;
            if (index == 0) {
                HomeScreen h = new HomeScreen(this, palette, this);
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
            } else if (index == 3) {
                CalendarScreen k = new CalendarScreen(this, palette);
                screen = k;
                view = k;
            } else if (index == 4) {
                NewsScreen n = new NewsScreen(this, palette);
                screen = n;
                view = n;
            } else if (index == 5) {
                SportScreen sp = new SportScreen(this, palette);
                screen = sp;
                view = sp;
            } else if (index == 6) {
                RemindersScreen r = new RemindersScreen(this, palette);
                screen = r;
                view = r;
            } else if (index == 7) {
                UsefulScreen u = new UsefulScreen(this, palette);
                screen = u;
                view = u;
            } else {
                SettingsScreen st = new SettingsScreen(this, palette, this);
                screen = st;
                view = st;
            }
            content.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            refreshScreen();
        }
        restartIdleTimer();
    }

    /** Postavke changed something: rebuild if the look changed, otherwise just redraw. */
    @Override
    public void onSettingsChanged() {
        if (!applyLook()) refreshScreen();
        refreshStatus();
        applyBrightness();
    }

    @Override
    public void previewNight() {
        previewUntil = System.currentTimeMillis() + 8000;
        brightUntil = 0;
        applyBrightness();
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
        if (!applyLook()) refreshScreen();
        refreshStatus();
        applyLowProfile();
        applyBrightness();
        restartIdleTimer();
    }

    @Override
    protected void onPause() {
        Scheduler.get(this).removeListener(this);
        unregisterReceiver(timeReceiver);
        unregisterReceiver(systemReceiver);
        handler.removeCallbacks(idleReturn);
        handler.removeCallbacks(lowProfile);
        handler.removeCallbacks(applyBrightness);
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
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            restartIdleTimer();
            if (Theme.night(this, Clock.now())) {
                brightUntil = System.currentTimeMillis() + NIGHT_TOUCH_MS;
                applyBrightness();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyLowProfile();
    }

    /** Night: the chosen low brightness (never off); a touch brightens for a minute. Day: the system setting. */
    private void applyBrightness() {
        handler.removeCallbacks(applyBrightness);
        long realNow = System.currentTimeMillis();
        boolean preview = realNow < previewUntil;
        boolean dim = preview || (Theme.night(this, Clock.now()) && realNow >= brightUntil);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float want = dim ? Math.max(0.05f, Settings.of(this).nightBrightness() / 100f) : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        if (lp.screenBrightness != want) {
            lp.screenBrightness = want;
            getWindow().setAttributes(lp);
        }
        long next = Math.max(preview ? previewUntil : 0, realNow < brightUntil ? brightUntil : 0);
        if (next > realNow) handler.postDelayed(applyBrightness, next - realNow + 50);
    }

    private void restartIdleTimer() {
        lastTouchAt = System.currentTimeMillis();
        handler.removeCallbacks(idleReturn);
        if (tab != 0) handler.postDelayed(idleReturn, IDLE_RETURN_MS);
    }

    private void onIdle() {
        boolean busy = screen instanceof Busy && ((Busy) screen).busy();
        if (busy && System.currentTimeMillis() - lastTouchAt < IDLE_RETURN_BUSY_MS) {
            handler.postDelayed(idleReturn, IDLE_RETURN_MS);
            return;
        }
        onTab(0);
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
        if (status == null) return;
        Scheduler.Summary s = Scheduler.get(this).summary();
        String sync = s.lastOkAt == 0 ? "--:--" : Fmt.hhmm(s.lastOkAt);
        int level = (!wifiOk || s.ok < s.total) ? 1 : 0;
        status.setLeft("Sync " + sync + " · " + s.ok + "/" + s.total + " izvora OK · WiFi " + (wifiOk ? "OK" : "nema"), level);
        String battery = batteryTenthsC == Integer.MIN_VALUE ? "--" : String.format(Locale.ROOT, "%.0f", batteryTenthsC / 10f);
        status.setRight("Baterija " + battery + " °C");
    }
}
