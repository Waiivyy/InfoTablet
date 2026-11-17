package ba.infotablet.home;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import ba.infotablet.R;
import ba.infotablet.ui.FontSpan;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.update.UpdateState;
import ba.infotablet.update.Updater;

/**
 * "Nova verzija" with an Instaliraj button, in the banner row of Početna when a downloaded and
 * verified update is waiting. A weather or smog warning takes the row first.
 */
public final class UpdateBanner extends LinearLayout {

    private final Palette p;
    private final TextView text;
    private String shown;

    public UpdateBanner(Context c, Palette p) {
        super(c);
        this.p = p;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundDrawable(Ui.rounded(c, p.accentTint, 0, 8));
        setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 6), 0);

        View icon = Ui.icon(c, R.drawable.ic_download, p.accent, 22);
        LayoutParams il = new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
        il.rightMargin = Ui.dp(c, 12);
        addView(icon, il);

        text = Ui.text(c, 15, Fonts.regular(), p.fg2);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView install = Ui.button(c, p, "Instaliraj", true, 15);
        install.setOnClickListener(v -> Updater.install(v.getContext()));
        LayoutParams bl = new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(c, 36));
        bl.leftMargin = Ui.dp(c, 12);
        addView(install, bl);
        setVisibility(GONE);
    }

    /** Shows the banner when an update is waiting and {@code room} is true (no warning in the row). */
    public void bind(boolean room) {
        Context c = getContext();
        UpdateState st = UpdateState.of(c);
        String version = st.readyVersion();
        if (!room || version == null || st.readyFile(c) == null) {
            setVisibility(GONE);
            return;
        }
        if (!version.equals(shown)) {
            shown = version;
            SpannableStringBuilder b = new SpannableStringBuilder("Nova verzija " + version);
            b.setSpan(new FontSpan(Fonts.medium()), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new AbsoluteSizeSpan(Ui.dp(c, 16.5f)), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new android.text.style.ForegroundColorSpan(p.fg), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.append("  ·  preuzeta je i spremna; postavke i podsjetnici ostaju");
            text.setText(b);
        }
        setVisibility(VISIBLE);
    }
}
