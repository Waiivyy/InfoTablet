package ba.infotablet.alarm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import ba.infotablet.reminders.Agenda;
import ba.infotablet.reminders.Reminder;
import ba.infotablet.reminders.ReminderStore;
import ba.infotablet.reminders.ReminderText;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Clock;
import ba.infotablet.util.Fmt;

/**
 * Full-screen reminder card that rings until "Gotovo" or "Odgodi 10 min". Unanswered, it snoozes
 * itself after five minutes; after the third unanswered ring it goes quiet and the reminder stays
 * on Početna as missed. Several reminders due together are shown one after another.
 */
public final class AlarmActivity extends Activity {

    private static volatile boolean showing;
    private static volatile long ringMs = Agenda.RING_MS;

    private final Handler handler = new Handler();
    private final Palette p = Palette.LIGHT;
    private AlarmSound sound;
    private Reminder current;
    private long currentDue;
    private long ringEndsAt;

    private ImageView icon;
    private TextView head, more, text, sub, hint;

    public static boolean isShowing() {
        return showing;
    }

    /** Debug builds only (DebugTimeReceiver): a shorter ring to test the unanswered path; 0 restores it. */
    public static void setDebugRingMs(long ms) {
        ringMs = ms > 0 ? ms : Agenda.RING_MS;
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        showing = true;
        Fonts.load(this);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1f;
        getWindow().setAttributes(lp);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        sound = new AlarmSound(this);
        buildUi();
        bind();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        bind();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            AlarmScheduler.releaseWakeLock(); // the window keeps the screen on from here
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.setPadding(Ui.dp(this, 40), Ui.dp(this, 30), Ui.dp(this, 40), Ui.dp(this, 22));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        icon = Ui.icon(this, ba.infotablet.R.drawable.ic_bell, p.accent, 34);
        top.addView(icon);
        head = Ui.text(this, 17, Fonts.medium(), p.fg2);
        head.setSingleLine(true);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        hl.leftMargin = Ui.dp(this, 14);
        top.addView(head, hl);
        more = Ui.text(this, 15, Fonts.medium(), p.accent);
        top.addView(more);
        root.addView(top);

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER);
        text = Ui.text(this, 46, Fonts.medium(), p.fg);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(3);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setLineSpacing(Ui.dp(this, 4), 1f);
        middle.addView(text, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sub = Ui.text(this, 21, Fonts.regular(), p.fg2);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(this, 18);
        middle.addView(sub, sl);
        root.addView(middle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        TextView snooze = Ui.button(this, p, "Odgodi 10 min", false, 24);
        snooze.setOnClickListener(v -> snooze());
        buttons.addView(snooze, new LinearLayout.LayoutParams(0, Ui.dp(this, 92), 1f));
        TextView done = Ui.button(this, p, "Gotovo", true, 28);
        done.setOnClickListener(v -> done());
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, Ui.dp(this, 92), 1.4f);
        dl.leftMargin = Ui.dp(this, 20);
        buttons.addView(done, dl);
        root.addView(buttons);

        hint = Ui.text(this, 15, Fonts.regular(), p.fg2);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        il.topMargin = Ui.dp(this, 14);
        root.addView(hint, il);
        setContentView(root);
    }

    private void bind() {
        long now = Clock.now();
        List<Reminder> ringing = Agenda.ringing(ReminderStore.all(this), now);
        if (ringing.isEmpty()) {
            close();
            return;
        }
        current = ringing.get(0);
        currentDue = Agenda.due(current, now);
        if (!sound.playing()) {
            sound.start();
            ringEndsAt = now + ringMs;
            handler.removeCallbacks(timeout);
            handler.postDelayed(timeout, ringMs);
        }

        icon.setImageResource(ReminderText.icon(current.category));
        icon.getDrawable().mutate().setColorFilter(p.accent, android.graphics.PorterDuff.Mode.SRC_IN);
        String cat = Reminder.categoryName(current.category);
        head.setText((cat.isEmpty() ? "Podsjetnik" : cat) + " · " + Fmt.hhmm(currentDue));
        more.setText(ringing.size() > 1 ? "još " + (ringing.size() - 1) : "");
        text.setText(current.text);
        String when = Fmt.dayOffset(now, currentDue) == 0 ? "Danas u " + Fmt.hhmm(currentDue)
                : "Zakazano za " + ReminderText.when(now, currentDue);
        sub.setText(current.repeat == Reminder.ONCE ? when : when + " · " + ReminderText.repeat(current));
        boolean last = current.snoozeFor == currentDue && current.rings >= Agenda.MAX_RINGS - 1;
        hint.setText(last
                ? "Bez odgovora zvono staje u " + Fmt.hhmm(ringEndsAt) + ", a podsjetnik ostaje na početnom ekranu."
                : "Bez odgovora zvono staje u " + Fmt.hhmm(ringEndsAt) + " i javlja se ponovo za 10 minuta.");
    }

    private void done() {
        ReminderStore.done(this, current.id, Clock.now());
        bind();
    }

    private void snooze() {
        long now = Clock.now();
        ReminderStore.snooze(this, current.id, currentDue, now + Agenda.SNOOZE_MS);
        bind();
    }

    private final Runnable timeout = () -> {
        long now = Clock.now();
        for (Reminder r : Agenda.ringing(ReminderStore.all(this), now)) {
            ReminderStore.unanswered(this, r.id, Agenda.due(r, now), now);
        }
        close();
    };

    /** Home pressed while ringing: treat it as "Odgodi", so the reminder is not lost. */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isFinishing()) return;
        long now = Clock.now();
        for (Reminder r : Agenda.ringing(ReminderStore.all(this), now)) {
            ReminderStore.snooze(this, r.id, Agenda.due(r, now), now + Agenda.SNOOZE_MS);
        }
        close();
    }

    // The card must be answered with one of its two buttons.
    @SuppressLint({"GestureBackNavigation", "MissingSuperCall"})
    @Override
    public void onBackPressed() {
        // ignored
    }

    private void close() {
        handler.removeCallbacks(timeout);
        sound.stop();
        if (!isFinishing()) finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        sound.stop();
        showing = false;
        AlarmScheduler.releaseWakeLock();
        super.onDestroy();
    }
}
