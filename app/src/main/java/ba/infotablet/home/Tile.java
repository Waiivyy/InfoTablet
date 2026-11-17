package ba.infotablet.home;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;

/** Shared frame of the optional Početna tiles: a card with a label row and an empty state. */
abstract class Tile extends LinearLayout {

    protected final Palette p;

    Tile(Context c, Palette p) {
        super(c);
        this.p = p;
        setOrientation(VERTICAL);
        setBackgroundDrawable(Ui.panel(c, p));
        setPadding(Ui.dp(c, 16), Ui.dp(c, 13), Ui.dp(c, 16), Ui.dp(c, 10));
    }

    abstract void bind(long now);

    protected LinearLayout head(String left, String right) {
        Context c = getContext();
        LinearLayout h = new LinearLayout(c);
        h.addView(Ui.label(c, p, left), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        if (right != null) h.addView(Ui.label(c, p, right));
        addView(h);
        return h;
    }

    protected void empty(String main, String hint) {
        Context c = getContext();
        TextView t = Ui.text(c, 16, Fonts.regular(), p.fg2);
        t.setText(main);
        t.setGravity(Gravity.CENTER);
        addView(t, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        if (hint != null) {
            TextView h = Ui.text(c, 13.5f, Fonts.regular(), p.fg3);
            h.setText(hint);
            h.setGravity(Gravity.CENTER);
            h.setPadding(0, 0, 0, Ui.dp(c, 8));
            addView(h, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    protected TextView note(String s) {
        TextView n = Ui.text(getContext(), 12.5f, Fonts.medium(), p.warn);
        n.setText(s);
        n.setSingleLine(true);
        return n;
    }
}
