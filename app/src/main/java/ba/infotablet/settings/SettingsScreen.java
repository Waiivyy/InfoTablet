package ba.infotablet.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ba.infotablet.Settings;
import ba.infotablet.security.Pin;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Clock;
import ba.infotablet.web.WebServer;

/**
 * Postavke, behind the PIN (set on first use). Sections on the left, their settings on the right.
 * Stays open while a change rebuilds the screens (theme, tiles) and locks again when the screen is
 * left or after two idle minutes (the return to Početna).
 */
public final class SettingsScreen extends FrameLayout implements Refreshable {

    /** Implemented by the activity: apply a change (it may rebuild every view, this one included). */
    public interface Host {
        void onSettingsChanged();

        /** Shows the night brightness for a few seconds, to try a level. */
        void previewNight();
    }

    static final String[] SECTIONS = {"Izgled", "Početni ekran", "Noć", "Lokacija", "Sadržaj", "Izvori podataka",
            "Wi-Fi stranica", "Sigurnost i podaci", "Ažuriranja", "O aplikaciji"};

    private enum Mode { NEW_PIN, CONFIRM_PIN, ENTER_PIN, CHANGE_OLD, OPEN }

    // Kept across rebuilds of the activity's views, cleared by lock().
    private static boolean unlocked;
    private static int section;
    private static int scrollY;

    final Palette p;
    final Host host;
    private Mode mode;
    private String firstPin;
    private PinPad currentPad;
    private ScrollView scroll;
    private LinearLayout body;
    private LinearLayout nav;
    private String shownKey = "";

    public SettingsScreen(Context c, Palette p, Host host) {
        super(c);
        this.p = p;
        this.host = host;
        mode = unlocked ? Mode.OPEN : Settings.of(c).hasPin() ? Mode.ENTER_PIN : Mode.NEW_PIN;
        show();
    }

    /** Called when Postavke are left: the PIN is needed again. */
    public static void lock() {
        unlocked = false;
        scrollY = 0;
    }

    @Override
    public void refresh(long now) {
        // Live parts (source status, Wi-Fi address) follow the data; rebuild only when they change.
        if (mode != Mode.OPEN) return;
        String key = Sections.liveKey(this, section);
        if (!key.equals(shownKey)) {
            scrollY = scroll.getScrollY();
            showSection(section, true);
        }
    }

    private void show() {
        removeAllViews();
        if (mode == Mode.OPEN) {
            showOpen();
        } else {
            showPad();
        }
    }

    // ---- PIN ----

    private void showPad() {
        Context c = getContext();
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundDrawable(Ui.panel(c, p));
        PinPad pad = new PinPad(c, p, this::onPin);
        switch (mode) {
            case NEW_PIN:
                pad.setTitle("Postavite PIN za Postavke");
                pad.setHint("4 do 8 cifara. Isti PIN otvara i Wi-Fi stranicu za podsjetnike.");
                break;
            case CONFIRM_PIN:
                pad.setTitle("Ponovite isti PIN");
                pad.setHint("Za potvrdu.");
                break;
            case CHANGE_OLD:
                pad.setTitle("Unesite sadašnji PIN");
                pad.setHint("Zatim birate novi.");
                break;
            default:
                pad.setTitle("Unesite PIN");
                pad.setHint("Postavke su zaključane da se ništa ne promijeni slučajno.");
        }
        panel.addView(pad);
        currentPad = pad;
        int m = Ui.pagePad(c);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        lp.setMargins(m, m, m, m);
        addView(panel, lp);
        long locked = Pin.lockedFor(Clock.now());
        if (locked > 0) pad.showMessage(lockedText(locked));
    }

    private static String lockedText(long ms) {
        return "Previše pogrešnih pokušaja. Sačekajte " + Math.max(1, (ms + 59_999) / 60_000) + " min.";
    }

    private void onPin(String pin) {
        Settings s = Settings.of(getContext());
        long now = Clock.now();
        switch (mode) {
            case NEW_PIN:
                if (!Pin.valid(pin)) {
                    currentPad.showMessage("PIN mora imati od 4 do 8 cifara");
                    currentPad.clear();
                    return;
                }
                firstPin = pin;
                mode = Mode.CONFIRM_PIN;
                show();
                return;
            case CONFIRM_PIN:
                if (!pin.equals(firstPin)) {
                    firstPin = null;
                    mode = Mode.NEW_PIN;
                    show();
                    currentPad.showMessage("PIN-ovi se ne slažu, počnite ponovo");
                    return;
                }
                s.setPinHash(Pin.hash(pin));
                firstPin = null;
                WebServer.update(getContext());
                open();
                return;
            case CHANGE_OLD:
            case ENTER_PIN:
                if (Pin.attempt(pin, s.pinHash(), now)) {
                    if (mode == Mode.CHANGE_OLD) {
                        mode = Mode.NEW_PIN;
                        show();
                    } else {
                        open();
                    }
                } else {
                    long locked = Pin.lockedFor(now);
                    currentPad.showMessage(locked > 0 ? lockedText(locked) : "Pogrešan PIN");
                    currentPad.clear();
                }
                return;
            default:
        }
    }

    private void open() {
        unlocked = true;
        mode = Mode.OPEN;
        show();
    }

    void changePin() {
        unlocked = false;
        mode = Mode.CHANGE_OLD;
        show();
    }

    void lockNow() {
        lock();
        mode = Mode.ENTER_PIN;
        show();
    }

    // ---- Sections ----

    private void showOpen() {
        Context c = getContext();
        LinearLayout root = new LinearLayout(c);
        int pad = Ui.pagePad(c);
        root.setPadding(pad, pad, pad, pad);

        nav = new LinearLayout(c);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setBackgroundDrawable(Ui.panel(c, p));
        nav.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        root.addView(nav, new LinearLayout.LayoutParams(Ui.dp(c, 204), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setBackgroundDrawable(Ui.panel(c, p));
        scroll = new ScrollView(c);
        scroll.setFadingEdgeLength(0);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(c, 20), Ui.dp(c, 14), Ui.dp(c, 20), Ui.dp(c, 16));
        scroll.addView(body);
        right.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        rl.leftMargin = Ui.gap(c);
        root.addView(right, rl);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        showSection(section, true);
    }

    private void bindNav() {
        Context c = getContext();
        nav.removeAllViews();
        for (int i = 0; i < SECTIONS.length; i++) {
            final int index = i;
            boolean on = i == section;
            TextView t = Ui.text(c, 15.5f, Fonts.medium(), on ? p.accent : p.fg);
            Ui.capSize(t, 15.5f, 1.1f);
            t.setText(SECTIONS[i]);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setSingleLine(true);
            t.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
            if (on) t.setBackgroundDrawable(Ui.rounded(c, p.accentTint, 0, 6));
            t.setClickable(true);
            t.setOnClickListener(v -> {
                scrollY = 0;
                showSection(index, false);
            });
            nav.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
    }

    /** Builds a section; keepScroll restores the scroll position after a change. */
    void showSection(int index, boolean keepScroll) {
        section = index;
        bindNav();
        body.removeAllViews();
        TextView title = Ui.label(getContext(), p, SECTIONS[index]);
        title.setPadding(0, 0, 0, Ui.dp(getContext(), 4));
        body.addView(title);
        Sections.build(this, index, body);
        shownKey = Sections.liveKey(this, index);
        final int y = keepScroll ? scrollY : 0;
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    /** A setting changed: remember the scroll position, let the activity apply it, then redraw this section. */
    void changed() {
        scrollY = scroll == null ? 0 : scroll.getScrollY();
        host.onSettingsChanged();
        // If the activity rebuilt its views, this screen is gone and a new one shows the section.
        if (getWindowToken() != null && mode == Mode.OPEN) showSection(section, true);
    }
}
