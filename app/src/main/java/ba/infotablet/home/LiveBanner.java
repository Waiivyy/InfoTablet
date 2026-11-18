package ba.infotablet.home;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import ba.infotablet.R;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.LiveSource;
import ba.infotablet.ui.FontSpan;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;

/** "UŽIVO  Bayern 2:1 Dortmund" in the banner row of Početna while the followed club plays. */
public final class LiveBanner extends LinearLayout {

    private final Palette p;
    private final TextView text, more;
    private String shown = "";

    public LiveBanner(Context c, Palette p) {
        super(c);
        this.p = p;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundDrawable(Ui.rounded(c, p.accentTint, 0, 8));
        setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
        LayoutParams il = new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
        il.rightMargin = Ui.dp(c, 12);
        addView(Ui.icon(c, R.drawable.ic_sport, p.accent, 22), il);
        text = Ui.text(c, 15, Fonts.regular(), p.fg2);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        more = Ui.text(c, 13, Fonts.regular(), p.fg2);
        more.setText("Sport");
        addView(more);
        setVisibility(GONE);
    }

    public boolean bind(boolean room, long now) {
        SourceState st = Cache.get(getContext(), LiveSource.ID);
        JSONObject m = null;
        if (room && st != null && st.hasData() && now - st.okAt < 10 * 60_000L) {
            try {
                m = new JSONObject(st.payload);
            } catch (JSONException ignored) {
                m = null;
            }
        }
        String status = m == null ? "" : m.optString("status");
        if (!"live".equals(status) && !"paused".equals(status)) {
            setVisibility(GONE);
            return false;
        }
        String key = st.okAt + status;
        if (!key.equals(shown)) {
            shown = key;
            Context c = getContext();
            SpannableStringBuilder b = new SpannableStringBuilder("paused".equals(status) ? "POLUVRIJEME  " : "UŽIVO  ");
            b.setSpan(new FontSpan(Fonts.medium()), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ForegroundColorSpan(p.alert), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int start = b.length();
            b.append(m.optString("home")).append("  ").append(String.valueOf(Math.max(0, m.optInt("hs")))).append(":")
                    .append(String.valueOf(Math.max(0, m.optInt("as")))).append("  ").append(m.optString("away"));
            b.setSpan(new FontSpan(Fonts.medium()), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new AbsoluteSizeSpan(Ui.dp(c, 16.5f)), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ForegroundColorSpan(p.fg), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setText(b);
        }
        setVisibility(VISIBLE);
        return true;
    }
}
