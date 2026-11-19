package ba.infotablet.news;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Fmt;

/** Top headline card on Početna. */
public final class NewsPanel extends LinearLayout {

    private final Palette p;

    public NewsPanel(Context c, Palette p) {
        super(c);
        this.p = p;
        setOrientation(VERTICAL);
        setBackgroundDrawable(Ui.panel(c, p));
        setPadding(Ui.dp(c, 16), Ui.dp(c, 13), Ui.dp(c, 16), Ui.dp(c, 12));
    }

    public void bind(long now) {
        removeAllViews();
        Context c = getContext();
        List<NewsItems.Entry> all = NewsItems.load(c, now, null);
        NewsItems.Entry pick = null;
        for (NewsItems.Entry e : all) { // newest item that already has its lead sentences
            if (!e.item.optString("lead").isEmpty()) {
                pick = e;
                break;
            }
        }
        if (pick == null && !all.isEmpty()) pick = all.get(0);

        LinearLayout head = new LinearLayout(c);
        head.addView(Ui.label(c, p, "Vijesti"), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        if (pick != null) head.addView(Ui.label(c, p, pick.portal + " · " + Fmt.hhmm(pick.item.optLong("time"))));
        addView(head);

        if (pick == null) {
            TextView t = Ui.text(c, 16, Fonts.regular(), p.fg3);
            t.setText("Nema podataka");
            t.setGravity(Gravity.CENTER);
            addView(t, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
            return;
        }
        TextView title = Ui.text(c, 18, Fonts.medium(), p.fg);
        title.setText(pick.item.optString("title"));
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(Ui.dp(c, 3), 1f);
        LayoutParams tl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(c, 6);
        addView(title, tl);
        TextView lead = Ui.fitText(c, 14, Fonts.regular(), p.fg2);
        lead.setText(pick.item.optString("lead"));
        lead.setLineSpacing(Ui.dp(c, 2), 1f);
        LayoutParams ll = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
        ll.topMargin = Ui.dp(c, 6);
        addView(lead, ll);
        if (pick.stale) {
            title.setAlpha(Freshness.STALE_ALPHA);
            lead.setAlpha(Freshness.STALE_ALPHA);
        }
    }
}
