package ba.infotablet.home;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import ba.infotablet.R;
import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.GasSource;
import ba.infotablet.sources.HeatSource;
import ba.infotablet.sources.PowerSource;
import ba.infotablet.sources.StreetNotices;
import ba.infotablet.sources.WaterSource;
import ba.infotablet.ui.FontSpan;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Fmt;

/**
 * A water, power, heating or gas notice for the configured streets, today or tomorrow, in the
 * banner row of Početna. A weather or smog warning takes the row first; a tap opens Korisno.
 */
public final class NoticeBanner extends LinearLayout {

    private final Palette p;
    private final ImageView icon;
    private final TextView text, more;
    private String shown = "";
    private String memoKey = "";
    private List<StreetNotices.Notice> memo;

    public NoticeBanner(Context c, Palette p) {
        super(c);
        this.p = p;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundDrawable(Ui.rounded(c, p.warnTint, p.warnLine, 8));
        setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
        icon = Ui.icon(c, R.drawable.ic_bolt, p.warn, 22);
        LayoutParams il = new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
        il.rightMargin = Ui.dp(c, 12);
        addView(icon, il);
        text = Ui.text(c, 15, Fonts.regular(), p.fg2);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        more = Ui.text(c, 13, Fonts.regular(), p.fg2);
        LayoutParams ml = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        ml.leftMargin = Ui.dp(c, 12);
        addView(more, ml);
        setVisibility(GONE);
    }

    /** Shows the most urgent notice when {@code room} is true. Returns whether the banner is up. */
    public boolean bind(boolean room, long now) {
        Context c = getContext();
        if (!room) {
            setVisibility(GONE);
            return false;
        }
        long today = Fmt.startOfDay(now);
        String streets = Settings.of(c).streets();
        SourceState w = Cache.get(c, WaterSource.ID), pw = Cache.get(c, PowerSource.ID), h = Cache.get(c, HeatSource.ID), g = Cache.get(c, GasSource.ID);
        String key0 = (now / 600_000L) + "/" + streets + "/" + okAt(w) + "/" + okAt(pw) + "/" + okAt(h) + "/" + okAt(g);
        if (!key0.equals(memoKey)) {   // parse the payloads only when one of them changed
            memoKey = key0;
            memo = StreetNotices.soon(StreetNotices.all(payload(w), payload(pw), payload(h), payload(g), streets, now), today);
        }
        List<StreetNotices.Notice> soon = memo;
        if (soon.isEmpty()) {
            setVisibility(GONE);
            return false;
        }
        StreetNotices.Notice n = soon.get(0);
        String key = n.kind + n.day + n.title + n.detail + soon.size();
        if (!key.equals(shown)) {
            shown = key;
            int res = StreetNotices.POWER.equals(n.kind) ? R.drawable.ic_bolt : StreetNotices.HEAT.equals(n.kind) ? R.drawable.ic_flame
                    : StreetNotices.GAS.equals(n.kind) ? R.drawable.ic_flame : R.drawable.ic_droplet;
            icon.setImageResource(res);
            icon.setColorFilter(p.warn);
            SpannableStringBuilder b = new SpannableStringBuilder();
            String when = n.announcement ? "Najava" : Fmt.dayOffset(now, n.day) == 0 ? "Danas" : "Sutra";
            b.append(when).append(": ").append(n.title);
            b.setSpan(new FontSpan(Fonts.medium()), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new AbsoluteSizeSpan(Ui.dp(c, 16.5f)), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ForegroundColorSpan(p.fg), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.append("  ·  ").append(n.detail);
            text.setText(b);
            more.setText(soon.size() > 1 ? "još " + (soon.size() - 1) + " · Korisno" : "Korisno");
        }
        setVisibility(VISIBLE);
        return true;
    }

    private static String payload(SourceState st) {
        return st == null ? null : st.payload;
    }

    private static long okAt(SourceState st) {
        return st == null ? 0 : st.okAt;
    }
}
