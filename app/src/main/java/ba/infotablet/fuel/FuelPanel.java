package ba.infotablet.fuel;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import ba.infotablet.data.SourceState;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Fmt;

/** "Gorivo danas" card on Početna: cheapest Dizel and Super 95 (prices marked as outliers are skipped). */
public final class FuelPanel extends LinearLayout {

    private static final String[][] SHOWN = {{"dizel", "Dizel"}, {"super95", "Super 95"}};

    private final Palette p;

    public FuelPanel(Context c, Palette p) {
        super(c);
        this.p = p;
        setOrientation(VERTICAL);
        setBackgroundDrawable(Ui.panel(c, p));
        setPadding(Ui.dp(c, 16), Ui.dp(c, 13), Ui.dp(c, 16), Ui.dp(c, 8));
    }

    public void bind(SourceState st, long staleAfterMs, long now) {
        removeAllViews();
        Context c = getContext();
        FuelData d = FuelData.of(st, staleAfterMs, now);

        LinearLayout head = new LinearLayout(c);
        head.addView(Ui.label(c, p, "Gorivo danas"), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.label(c, p, "KM/l"));
        addView(head);

        if (d.payload == null) {
            TextView t = Ui.text(c, 16, Fonts.regular(), p.fg3);
            t.setText(d.note);
            t.setGravity(Gravity.CENTER);
            addView(t, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
            return;
        }
        for (int i = 0; i < SHOWN.length; i++) {
            if (i > 0) addView(Ui.hairline(c, p.soft), new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
            View row = row(c, SHOWN[i][1], d.fuel(SHOWN[i][0]));
            if (d.stale) row.setAlpha(Freshness.STALE_ALPHA);
            addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        }
        if (d.stale && d.note != null) {
            TextView n = Ui.text(c, 12.5f, Fonts.medium(), p.warn);
            n.setText(d.note);
            n.setSingleLine(true);
            addView(n);
        }
    }

    private View row(Context c, String label, JSONObject fuel) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(VERTICAL);
        left.addView(Ui.label(c, p, label));
        JSONObject best = fuel == null ? null : fuel.optJSONObject("cheapest");
        TextView name = Ui.text(c, 14.5f, Fonts.regular(), p.fg);
        name.setText(best == null ? "Nema podataka" : best.optString("name"));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams nl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        nl.topMargin = Ui.dp(c, 4);
        left.addView(name, nl);
        if (best != null) {
            TextView addr = Ui.text(c, 13, Fonts.regular(), p.fg2);
            addr.setText(best.optString("address"));
            addr.setSingleLine(true);
            addr.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams al = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            al.topMargin = Ui.dp(c, 2);
            left.addView(addr, al);
        }
        r.addView(left, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView price = Ui.text(c, 34, Fonts.mono(), p.fg);
        price.setText(NarrowSpan.numeric(best == null ? "-" : Fmt.dec2(best.optDouble("price"))));
        r.addView(price);
        return r;
    }
}
