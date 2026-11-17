package ba.infotablet.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Bottom status line, for example "SYNC 14:32 · 7/8 IZVORA OK · WIFI OK" and "BATERIJA 31 °C". */
public final class StatusLine extends LinearLayout {

    private final Palette p;
    private final TextView left;
    private final TextView right;
    private final GradientDrawable dot;

    public StatusLine(Context c, Palette p) {
        super(c);
        this.p = p;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(c, 13), 0, Ui.dp(c, 14), 0);

        dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        int d = Ui.dp(c, 7);
        dot.setSize(d, d);
        dot.setColor(p.ok);

        left = Ui.text(c, 11, Fonts.monoMedium(), p.fg2);
        left.setSingleLine(true);
        left.setCompoundDrawablesWithIntrinsicBounds(dot, null, null, null);
        left.setCompoundDrawablePadding(Ui.dp(c, 7));
        addView(left, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        right = Ui.text(c, 11, Fonts.monoMedium(), p.fg2);
        right.setSingleLine(true);
        addView(right, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    /** level: 0 ok, 1 warn, 2 alert. */
    public void setLeft(String text, int level) {
        dot.setColor(level == 0 ? p.ok : level == 1 ? p.warn : p.alert);
        left.setText(TrackingSpan.of(text.toUpperCase(Locale.ROOT), 0.05f));
    }

    public void setRight(String text) {
        right.setText(TrackingSpan.of(text.toUpperCase(Locale.ROOT), 0.05f));
    }
}
