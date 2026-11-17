package ba.infotablet.settings;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import ba.infotablet.R;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;

/** The building blocks of Postavke: a row with title and description, choices, switches and steppers. */
final class Controls {

    interface Pick {
        void on(int index);
    }

    private Controls() {}

    /** Title (and optional description) on the left, the control on the right. */
    static LinearLayout row(Context c, Palette p, String title, String desc, View control) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tl.rightMargin = Ui.dp(c, 16);
        r.addView(titleBlock(c, p, title, desc), tl);
        if (control != null) r.addView(control);
        return r;
    }

    /** A control below its title, for wide controls (several choices, a text field). */
    static LinearLayout block(Context c, Palette p, String title, String desc, View control) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
        b.addView(titleBlock(c, p, title, desc), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Ui.dp(c, 10);
        b.addView(control, cl);
        return b;
    }

    private static LinearLayout titleBlock(Context c, Palette p, String title, String desc) {
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(c, 16.5f, Fonts.medium(), p.fg);
        t.setText(title);
        text.addView(t);
        if (desc != null && !desc.isEmpty()) {
            TextView d = Ui.text(c, 13.5f, Fonts.regular(), p.fg2);
            d.setText(desc);
            d.setLineSpacing(Ui.dp(c, 2), 1f);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dl.topMargin = Ui.dp(c, 3);
            text.addView(d, dl);
        }
        return text;
    }

    static View divider(Context c, Palette p) {
        View v = Ui.hairline(c, p.soft);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
        return v;
    }

    /** Equal-width choice buttons; the chosen one is filled with the accent. */
    static LinearLayout choice(Context c, Palette p, String[] labels, int selected, Pick pick) {
        LinearLayout l = new LinearLayout(c);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean on = i == selected;
            TextView b = Ui.text(c, 15, Fonts.medium(), on ? p.accentFg : p.fg);
            b.setText(labels[i]);
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            b.setEllipsize(TextUtils.TruncateAt.END);
            b.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
            b.setBackgroundDrawable(toggleBg(c, p, on));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                if (index != selected) pick.on(index);
            });
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f);
            if (i > 0) bl.leftMargin = Ui.dp(c, 6);
            l.addView(b, bl);
        }
        return l;
    }

    static StateListDrawable toggleBg(Context c, Palette p, boolean on) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_pressed}, Ui.rounded(c, on ? p.accent : p.soft, on ? 0 : p.line, 8));
        s.addState(new int[0], Ui.rounded(c, on ? p.accent : p.panel, on ? 0 : p.line, 8));
        return s;
    }

    static View toggle(Context c, Palette p, boolean on, SwitchView.Listener listener) {
        return new SwitchView(c, p, on, listener);
    }

    /** [minus] value [plus], or with arrows for lists. One step per tap (each step redraws the section). */
    static LinearLayout stepper(Context c, Palette p, CharSequence value, float valueWidthDp, boolean arrows, Runnable down, Runnable up) {
        LinearLayout l = new LinearLayout(c);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.addView(stepButton(c, p, arrows ? R.drawable.ic_chevron_left : R.drawable.ic_minus, down));
        TextView v = Ui.text(c, 16.5f, Fonts.medium(), p.fg);
        v.setText(value);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        l.addView(v, new LinearLayout.LayoutParams(Ui.dp(c, valueWidthDp), LinearLayout.LayoutParams.WRAP_CONTENT));
        l.addView(stepButton(c, p, arrows ? R.drawable.ic_chevron_right : R.drawable.ic_plus, up));
        return l;
    }

    private static View stepButton(Context c, Palette p, int icon, Runnable action) {
        LinearLayout b = new LinearLayout(c);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundDrawable(Ui.buttonBackground(c, p, false));
        b.setClickable(true);
        b.addView(Ui.icon(c, icon, p.fg, 20));
        b.setOnClickListener(v -> action.run());
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        return b;
    }

    static TextView button(Context c, Palette p, String label, boolean primary, View.OnClickListener click) {
        TextView b = Ui.button(c, p, label, primary, 15.5f);
        b.setOnClickListener(click);
        b.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 44)));
        return b;
    }

    static TextView note(Context c, Palette p, String s, int color) {
        TextView t = Ui.text(c, 14, Fonts.regular(), color);
        t.setText(s);
        t.setLineSpacing(Ui.dp(c, 2), 1f);
        t.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
        return t;
    }
}
