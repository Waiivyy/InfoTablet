package ba.infotablet.news;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.TrackingSpan;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Fmt;

/** Vijesti: category chips, portal status, and headline cards with the first sentences of each article. */
public final class NewsScreen extends FrameLayout implements Refreshable {

    private static final String[][] CHIPS = {
            {null, "Sve"}, {"bih", "BiH"}, {"sarajevo", "Sarajevo"}, {"svijet", "Svijet"}, {"ekonomija", "Ekonomija"}, {"kultura", "Kultura"}};
    private static final int MAX_CARDS = 30;

    private final Palette p;
    private final LinearLayout chips, sources, cards;
    private final ScrollView scroll;
    private int chip = 0;
    private String shownSignature = "";
    private long lastNow;

    public NewsScreen(Context c, Palette p) {
        super(c);
        this.p = p;
        int pad = Ui.pagePad(c);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        chips = new LinearLayout(c);
        top.addView(chips, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        sources = new LinearLayout(c);
        sources.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(sources);
        root.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 40)));

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setFadingEdgeLength(0);
        cards = new LinearLayout(c);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setPadding(0, Ui.dp(c, 10), 0, pad);
        scroll.addView(cards);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public void refresh(long now) {
        lastNow = now;
        Context c = getContext();
        String category = CHIPS[chip][0];
        String sig = NewsItems.signature(c, category);
        if (sig.equals(shownSignature)) return; // keep the reading position when nothing changed
        shownSignature = sig;
        bindChips(c);
        bindSources(c, now);
        bindCards(c, now, category);
    }

    private void bindChips(Context c) {
        chips.removeAllViews();
        for (int i = 0; i < CHIPS.length; i++) {
            final int index = i;
            if (CHIPS[i][0] != null && ba.infotablet.Settings.of(c).newsCategoryHidden(CHIPS[i][0])) continue; // hidden in Postavke
            boolean on = i == chip;
            TextView t = Ui.text(c, 15, Fonts.medium(), on ? p.accentFg : p.fg2);
            t.setText(CHIPS[i][1]);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
            t.setBackgroundDrawable(on ? Ui.rounded(c, p.accent, p.accent, 8) : Ui.rounded(c, p.panel, p.line, 8));
            t.setOnClickListener(v -> {
                chip = index;
                shownSignature = "";
                refresh(lastNow);
                scroll.scrollTo(0, 0);
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 36));
            if (i > 0) l.leftMargin = Ui.dp(c, 6);
            chips.addView(t, l);
        }
    }

    private void bindSources(Context c, long now) {
        sources.removeAllViews();
        for (NewsItems.PortalStatus s : NewsItems.status(c, now)) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(s.ok ? p.ok : p.warn);
            int d = Ui.dp(c, 7);
            dot.setSize(d, d);
            TextView t = Ui.text(c, 13, Fonts.regular(), p.fg2);
            t.setText(s.label);
            t.setCompoundDrawablesWithIntrinsicBounds(dot, null, null, null);
            t.setCompoundDrawablePadding(Ui.dp(c, 6));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            l.leftMargin = Ui.dp(c, 14);
            sources.addView(t, l);
        }
    }

    private void bindCards(Context c, long now, String category) {
        cards.removeAllViews();
        List<NewsItems.Entry> list = NewsItems.load(c, now, category);
        if (list.isEmpty()) {
            TextView t = Ui.text(c, 17, Fonts.regular(), p.fg3);
            t.setText(category == null ? "Nema podataka" : "Nema vijesti u ovoj kategoriji");
            t.setGravity(Gravity.CENTER);
            cards.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 300)));
            return;
        }
        int n = Math.min(MAX_CARDS, list.size());
        for (int i = 0; i < n; i += 2) {
            LinearLayout row = new LinearLayout(c);
            row.addView(card(c, list.get(i)), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            View right = i + 1 < n ? card(c, list.get(i + 1)) : new View(c);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            rl.leftMargin = Ui.dp(c, 10);
            row.addView(right, rl);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 238));
            if (i > 0) l.topMargin = Ui.dp(c, 10);
            cards.addView(row, l);
        }
    }

    private View card(Context c, NewsItems.Entry e) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundDrawable(Ui.panel(c, p));
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 12));

        LinearLayout meta = new LinearLayout(c);
        TextView src = Ui.label(c, p, e.portal + " · " + NewsItems.categoryLabel(e.item.optString("cat")));
        meta.addView(src, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (e.stale) {
            TextView tag = Ui.text(c, 11, Fonts.medium(), p.warn);
            tag.setText(TrackingSpan.of(("Nema podataka · zadnje " + staleTime(e)).toUpperCase(Locale.ROOT), 0.07f));
            meta.addView(tag);
        } else {
            TextView time = Ui.label(c, p, Fmt.hhmm(e.item.optLong("time")));
            time.setTypeface(Fonts.monoMedium());
            time.setText(NarrowSpan.numeric(Fmt.hhmm(e.item.optLong("time"))));
            meta.addView(time);
        }
        card.addView(meta);

        TextView title = Ui.text(c, 20, Fonts.medium(), p.fg);
        title.setText(e.item.optString("title"));
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(Ui.dp(c, 3), 1f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(c, 7);
        card.addView(title, tl);

        TextView lead = Ui.fitText(c, 15, Fonts.regular(), p.fg);
        String text = e.item.optString("lead");
        lead.setText(text.isEmpty() ? "Tekst članka trenutno nije dostupan." : text);
        if (text.isEmpty()) lead.setTextColor(p.fg3);
        lead.setLineSpacing(Ui.dp(c, 5), 1f);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        ll.topMargin = Ui.dp(c, 7);
        card.addView(lead, ll);

        if (e.stale) {
            src.setAlpha(Freshness.STALE_ALPHA + 0.15f);
            title.setAlpha(Freshness.STALE_ALPHA + 0.15f);
            lead.setAlpha(Freshness.STALE_ALPHA + 0.15f);
        }
        return card;
    }

    private static String staleTime(NewsItems.Entry e) {
        String note = e.staleNote == null ? "" : e.staleNote;
        int i = note.lastIndexOf("zadnje ");
        return i >= 0 ? note.substring(i + 7) : "";
    }
}
