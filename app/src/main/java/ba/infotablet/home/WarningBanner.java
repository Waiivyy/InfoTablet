package ba.infotablet.home;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import ba.infotablet.data.SourceState;
import ba.infotablet.ui.FontSpan;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.SmogText;
import ba.infotablet.ui.Ui;
import ba.infotablet.ui.WarningText;

/** One-line banner for the most relevant active weather warning; hidden when there is none. */
public final class WarningBanner extends LinearLayout {

    private final Palette p;

    public WarningBanner(Context c, Palette p) {
        super(c);
        this.p = p;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundDrawable(Ui.rounded(c, p.warnTint, p.warnLine, 8));
        setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
        setVisibility(GONE);
    }

    /**
     * Shows an active smog episode first, otherwise the first active weather warning (home region
     * first). Returns true when visible.
     */
    public boolean bind(SourceState st, SourceState smogState, long now) {
        removeAllViews();
        List<JSONObject> active = active(st, now);
        JSONObject smog = SmogText.active(smogState, now);
        Context c = getContext();
        if (smog != null) {
            String level = smog.optString("level");
            addView(Ui.chip(c, SmogText.levelName(level), SmogText.levelColor(level), 0xFFFFFFFF));
            View icon = Ui.icon(c, ba.infotablet.R.drawable.ic_fog, p.warn, 24);
            LayoutParams il = new LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
            il.leftMargin = Ui.dp(c, 12);
            il.rightMargin = Ui.dp(c, 12);
            addView(icon, il);
            SpannableStringBuilder b = new SpannableStringBuilder("Epizoda zagađenja zraka");
            b.setSpan(new FontSpan(Fonts.medium()), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new AbsoluteSizeSpan(Ui.dp(c, 16.5f)), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.append(" · ").append(SmogText.zones(smog, level)).append(" · ").append(SmogText.shortMeasure(level));
            TextView text = Ui.text(c, 15, Fonts.regular(), p.fg);
            text.setText(b);
            text.setSingleLine(true);
            text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            TextView s = Ui.text(c, 13, Fonts.regular(), p.fg2);
            s.setText(active.isEmpty() ? "Vlada KS" : "još " + active.size() + " · Vlada KS");
            LayoutParams sl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            sl.leftMargin = Ui.dp(c, 12);
            addView(s, sl);
            setVisibility(VISIBLE);
            return true;
        }
        if (active.isEmpty()) {
            setVisibility(GONE);
            return false;
        }
        JSONObject w = active.get(0);
        int level = w.optInt("level");
        addView(Ui.chip(c, WarningText.levelName(level), WarningText.levelColor(level), WarningText.levelTextColor(level)));
        View icon = Ui.icon(c, WarningText.icon(w.optInt("type")), p.warn, 24);
        LayoutParams il = new LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
        il.leftMargin = Ui.dp(c, 12);
        il.rightMargin = Ui.dp(c, 12);
        addView(icon, il);

        String[] parts = WarningText.line(w, now);
        SpannableStringBuilder b = new SpannableStringBuilder(parts[0]);
        b.setSpan(new FontSpan(Fonts.medium()), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new AbsoluteSizeSpan(Ui.dp(c, 16.5f)), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.append(parts[1]);
        TextView text = Ui.text(c, 15, Fonts.regular(), p.fg);
        text.setText(b);
        text.setSingleLine(true);
        addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        String src = "MeteoAlarm · " + WarningText.sender(w.optString("sender"));
        if (active.size() > 1) src = "još " + (active.size() - 1) + " · " + src;
        TextView s = Ui.text(c, 13, Fonts.regular(), p.fg2);
        s.setText(src);
        s.setSingleLine(true);
        LayoutParams sl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        sl.leftMargin = Ui.dp(c, 12);
        addView(s, sl);
        setVisibility(VISIBLE);
        return true;
    }

    static List<JSONObject> active(SourceState st, long now) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        if (!st.hasData()) return out;
        try {
            JSONArray ws = new JSONObject(st.payload).getJSONArray("warnings");
            for (int i = 0; i < ws.length(); i++) {
                JSONObject w = ws.getJSONObject(i);
                if (w.optLong("expires") > now) out.add(w);
            }
        } catch (JSONException ignored) {
            // treated as no warnings
        }
        return out;
    }
}
