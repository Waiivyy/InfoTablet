package ba.infotablet.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ba.infotablet.R;

/** The tab bar on the left: icon above label, one tab highlighted. */
public final class Rail extends LinearLayout {

    public interface Listener { void onTab(int index); }

    public static final String[] LABELS = {
            "Početna", "Vrijeme", "Gorivo", "Kalendar", "Vijesti", "Sport", "Podsjetnici", "Korisno", "Postavke"};
    private static final int[] ICONS = {
            R.drawable.ic_home, R.drawable.ic_weather, R.drawable.ic_fuel, R.drawable.ic_calendar, R.drawable.ic_news,
            R.drawable.ic_sport, R.drawable.ic_bell, R.drawable.ic_tools, R.drawable.ic_sliders};

    private final Palette p;
    private final LinearLayout[] tabs = new LinearLayout[LABELS.length];
    private final ImageView[] icons = new ImageView[LABELS.length];
    private final TextView[] labels = new TextView[LABELS.length];
    private int selected = -1;

    public Rail(Context c, Palette p, final Listener listener) {
        super(c);
        this.p = p;
        setOrientation(VERTICAL);
        setBackgroundColor(p.rail);
        int pad = Ui.dp(c, 6);
        setPadding(pad, pad, pad, pad);

        for (int i = 0; i < LABELS.length; i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(c);
            tab.setOrientation(VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setClickable(true);
            tab.setOnClickListener(v -> listener.onTab(index));

            ImageView icon = new ImageView(c);
            icon.setImageResource(ICONS[i]);
            tab.addView(icon, new LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));

            TextView label = Ui.text(c, 12, Fonts.medium(), p.fg2);
            label.setText(LABELS[i]);
            label.setSingleLine(true);
            LayoutParams ll = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            ll.topMargin = Ui.dp(c, 5);
            tab.addView(label, ll);

            LayoutParams tl = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
            tl.bottomMargin = i < LABELS.length - 1 ? Ui.dp(c, 2) : 0;
            addView(tab, tl);
            tabs[i] = tab;
            icons[i] = icon;
            labels[i] = label;
        }
    }

    public void select(int index) {
        if (index == selected) return;
        selected = index;
        Context c = getContext();
        for (int i = 0; i < tabs.length; i++) {
            boolean on = i == index;
            int color = on ? p.accent : p.fg2;
            tabs[i].setBackgroundDrawable(on ? Ui.rounded(c, p.accentTint, 0, 10) : null);
            Drawable d = icons[i].getDrawable().mutate();
            d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            labels[i].setTextColor(color);
        }
    }
}
