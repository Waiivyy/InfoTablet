package ba.infotablet.settings;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import ba.infotablet.R;
import ba.infotablet.security.Pin;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;

/** A large on-screen number pad with dots for the digits entered (no system keyboard). */
public final class PinPad extends LinearLayout {

    public interface Listener {
        /** Called with the digits when "U redu" is pressed. */
        void onPin(String pin);
    }

    private final Palette p;
    private final TextView title, hint, message;
    private final LinearLayout dots;
    private final StringBuilder digits = new StringBuilder();
    private final Listener listener;

    public PinPad(Context c, Palette p, Listener listener) {
        super(c);
        this.p = p;
        this.listener = listener;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.capBegin(1.1f);   // fixed-size keys: the pad must fit with every text size
        try {

            title = Ui.text(c, 22, Fonts.medium(), p.fg);
            title.setGravity(Gravity.CENTER);
            addView(title);
            hint = Ui.text(c, 15, Fonts.regular(), p.fg2);
            hint.setGravity(Gravity.CENTER);
            LayoutParams hl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            hl.topMargin = Ui.dp(c, 6);
            addView(hint, hl);
            dots = new LinearLayout(c);
            dots.setGravity(Gravity.CENTER);
            LayoutParams dl = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 22));
            dl.topMargin = Ui.dp(c, 16);
            addView(dots, dl);
            message = Ui.text(c, 14.5f, Fonts.medium(), p.alert);
            message.setGravity(Gravity.CENTER);
            message.setSingleLine(true);
            LayoutParams ml = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 22));
            ml.topMargin = Ui.dp(c, 8);
            addView(message, ml);

            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "del", "0", "ok"};
            for (int row = 0; row < 4; row++) {
                LinearLayout r = new LinearLayout(c);
                r.setBaselineAligned(false);   // "U redu" is smaller than the digits; keep the keys level
                for (int col = 0; col < 3; col++) {
                    final String k = keys[row * 3 + col];
                    View b;
                    if (k.equals("del")) {
                        LinearLayout wrap = new LinearLayout(c);
                        wrap.setGravity(Gravity.CENTER);
                        wrap.setBackgroundDrawable(Ui.buttonBackground(c, p, false));
                        wrap.setClickable(true);
                        wrap.addView(Ui.icon(c, R.drawable.ic_backspace, p.fg, 26));
                        wrap.setContentDescription("Obriši");
                        wrap.setOnClickListener(v -> delete());
                        b = wrap;
                    } else if (k.equals("ok")) {
                        TextView t = Ui.button(c, p, "U redu", true, 19);
                        t.setOnClickListener(v -> submit());
                        b = t;
                    } else {
                        TextView t = Ui.button(c, p, k, false, 26);
                        t.setTypeface(Fonts.mono());
                        t.setOnClickListener(v -> type(k));
                        b = t;
                    }
                    LayoutParams bl = new LayoutParams(Ui.dp(c, 104), Ui.dp(c, 62));
                    if (col > 0) bl.leftMargin = Ui.dp(c, 10);
                    r.addView(b, bl);
                }
                LayoutParams rl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                rl.topMargin = Ui.dp(c, 10);
                addView(r, rl);
            }
            renderDots();
        } finally {
            Ui.capEnd();
        }
    }

    public void setTitle(String s) {
        title.setText(s);
    }

    public void setHint(String s) {
        hint.setText(s);
    }

    public void showMessage(String s) {
        message.setText(s);
    }

    public void clear() {
        digits.setLength(0);
        renderDots();
    }

    private void type(String k) {
        if (digits.length() >= Pin.MAX_LENGTH) return;
        digits.append(k);
        message.setText("");
        renderDots();
    }

    private void delete() {
        if (digits.length() > 0) digits.setLength(digits.length() - 1);
        renderDots();
    }

    private void submit() {
        String pin = digits.toString();
        listener.onPin(pin);
    }

    private void renderDots() {
        Context c = getContext();
        dots.removeAllViews();
        int shown = Math.max(Pin.MIN_LENGTH, digits.length());
        for (int i = 0; i < shown; i++) {
            View d = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (i < digits.length()) {
                g.setColor(p.fg);
            } else {
                g.setColor(0x00000000);
                g.setStroke(Math.max(1, Ui.dp(c, 2)), p.line);
            }
            d.setBackgroundDrawable(g);
            LayoutParams l = new LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16));
            if (i > 0) l.leftMargin = Ui.dp(c, 14);
            dots.addView(d, l);
        }
    }
}
