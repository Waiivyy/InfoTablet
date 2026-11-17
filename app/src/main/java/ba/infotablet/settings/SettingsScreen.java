package ba.infotablet.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ba.infotablet.Settings;
import ba.infotablet.security.Pin;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.QrView;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Clock;
import ba.infotablet.web.WebServer;

/**
 * Postavke, behind the PIN (set on first use). Locks again whenever the screen is left. For now it
 * holds the Wi-Fi page (address and QR code) and the PIN; the other settings follow.
 */
public final class SettingsScreen extends FrameLayout implements Refreshable {

    private enum Mode { NEW_PIN, CONFIRM_PIN, ENTER_PIN, CHANGE_OLD, OPEN }

    private final Palette p;
    private Mode mode;
    private String firstPin;
    private String shownAddress = "?";

    public SettingsScreen(Context c, Palette p) {
        super(c);
        this.p = p;
        mode = Settings.of(c).hasPin() ? Mode.ENTER_PIN : Mode.NEW_PIN;
        show();
    }

    @Override
    public void refresh(long now) {
        // The address can change when the Wi-Fi reconnects.
        if (mode == Mode.OPEN && !String.valueOf(WebServer.address(getContext())).equals(shownAddress)) show();
    }

    private void show() {
        removeAllViews();
        if (mode == Mode.OPEN) {
            showSettings();
        } else {
            showPad();
        }
    }

    private void showPad() {
        Context c = getContext();
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundDrawable(Ui.panel(c, p));
        PinPad pad = new PinPad(c, p, this::onPin);
        String title, hint;
        switch (mode) {
            case NEW_PIN:
                title = "Postavite PIN za Postavke";
                hint = "4 do 8 cifara. Isti PIN otvara i Wi-Fi stranicu za podsjetnike.";
                break;
            case CONFIRM_PIN:
                title = "Ponovite isti PIN";
                hint = "Za potvrdu.";
                break;
            case CHANGE_OLD:
                title = "Unesite sadašnji PIN";
                hint = "Zatim birate novi.";
                break;
            default:
                title = "Unesite PIN";
                hint = "Postavke su zaključane da se ništa ne promijeni slučajno.";
        }
        pad.setTitle(title);
        pad.setHint(hint);
        panel.addView(pad);
        currentPad = pad;
        int m = Ui.dp(c, 12);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        lp.setMargins(m, m, m, m);
        addView(panel, lp);
        long locked = Pin.lockedFor(Clock.now());
        if (locked > 0) pad.showMessage("Previše pogrešnih pokušaja. Sačekajte " + Math.max(1, (locked + 59_999) / 60_000) + " min.");
    }

    private PinPad currentPad;

    private void onPin(String pin) {
        Settings s = Settings.of(getContext());
        PinPad pad = currentPad;
        long now = Clock.now();
        switch (mode) {
            case NEW_PIN:
                if (!Pin.valid(pin)) {
                    pad.showMessage("PIN mora imati od 4 do 8 cifara");
                    pad.clear();
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
                mode = Mode.OPEN;
                show();
                return;
            case CHANGE_OLD:
            case ENTER_PIN:
                if (Pin.attempt(pin, s.pinHash(), now)) {
                    mode = mode == Mode.CHANGE_OLD ? Mode.NEW_PIN : Mode.OPEN;
                    show();
                } else {
                    long locked = Pin.lockedFor(now);
                    pad.showMessage(locked > 0 ? "Previše pogrešnih pokušaja. Sačekajte " + Math.max(1, (locked + 59_999) / 60_000) + " min."
                            : "Pogrešan PIN");
                    pad.clear();
                }
                return;
            default:
        }
    }

    private void showSettings() {
        Context c = getContext();
        String address = WebServer.address(c);
        shownAddress = String.valueOf(address);
        Settings s = Settings.of(c);
        boolean on = s.webEnabled() && WebServer.isRunning();

        LinearLayout root = new LinearLayout(c);
        int pad = Ui.dp(c, 12);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackgroundDrawable(Ui.panel(c, p));
        left.setPadding(Ui.dp(c, 18), Ui.dp(c, 13), Ui.dp(c, 18), Ui.dp(c, 16));
        left.addView(Ui.label(c, p, "Wi-Fi stranica za podsjetnike"));

        TextView state = Ui.text(c, 20, Fonts.medium(), on && address != null ? p.ok : p.fg2);
        state.setText(!s.webEnabled() ? "Isključena" : address == null ? "Nema Wi-Fi veze" : on ? "Uključena" : "Ne radi");
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(c, 14);
        left.addView(state, sl);

        TextView about = Ui.text(c, 16, Fonts.regular(), p.fg);
        about.setLineSpacing(Ui.dp(c, 3), 1f);
        about.setText("Podsjetnike možete dodavati i mijenjati s telefona. Telefon mora biti na istoj kućnoj Wi-Fi mreži. "
                + "Otvorite adresu u pregledniku ili skenirajte kod kamerom, pa se prijavite istim PIN-om.");
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        al.topMargin = Ui.dp(c, 10);
        left.addView(about, al);

        TextView note = Ui.text(c, 13.5f, Fonts.regular(), p.fg2);
        note.setLineSpacing(Ui.dp(c, 2), 1f);
        note.setText("Stranica radi samo unutar kućne mreže i nije dostupna s interneta.");
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nl.topMargin = Ui.dp(c, 10);
        left.addView(note, nl);

        left.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1f));
        LinearLayout actions = new LinearLayout(c);
        TextView toggle = Ui.button(c, p, s.webEnabled() ? "Isključi stranicu" : "Uključi stranicu", false, 16);
        toggle.setOnClickListener(v -> {
            s.setWebEnabled(!s.webEnabled());
            WebServer.update(getContext());
            show();
        });
        actions.addView(toggle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 50)));
        TextView change = Ui.button(c, p, "Promijeni PIN", false, 16);
        change.setOnClickListener(v -> {
            mode = Mode.CHANGE_OLD;
            show();
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 50));
        cl.leftMargin = Ui.dp(c, 10);
        actions.addView(change, cl);
        actions.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView lock = Ui.button(c, p, "Zaključaj", false, 16);
        lock.setOnClickListener(v -> {
            mode = Mode.ENTER_PIN;
            show();
        });
        actions.addView(lock, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 50)));
        left.addView(actions);
        root.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        right.setBackgroundDrawable(Ui.panel(c, p));
        right.setPadding(Ui.dp(c, 18), Ui.dp(c, 13), Ui.dp(c, 18), Ui.dp(c, 16));
        TextView rl = Ui.label(c, p, "Adresa");
        right.addView(rl, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (on && address != null) {
            QrView qr = new QrView(c);
            qr.setText(address);
            LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(Ui.dp(c, 300), 0, 1f);
            ql.topMargin = Ui.dp(c, 10);
            right.addView(qr, ql);
            TextView addr = Ui.text(c, 20, Fonts.monoMedium(), p.fg);
            addr.setText(ba.infotablet.ui.NarrowSpan.numeric(address.replace("http://", "")));
            addr.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams adl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            adl.topMargin = Ui.dp(c, 12);
            right.addView(addr, adl);
        } else {
            TextView none = Ui.text(c, 16, Fonts.regular(), p.fg2);
            none.setText(!s.webEnabled() ? "Stranica je isključena." : "Kad se tablet spoji na Wi-Fi, ovdje se pojavi adresa i kod.");
            none.setGravity(Gravity.CENTER);
            right.addView(none, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Ui.dp(c, 372), LinearLayout.LayoutParams.MATCH_PARENT);
        rp.leftMargin = Ui.dp(c, 10);
        root.addView(right, rp);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }
}
