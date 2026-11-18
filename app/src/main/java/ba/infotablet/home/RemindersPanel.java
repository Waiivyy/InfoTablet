package ba.infotablet.home;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ba.infotablet.R;
import ba.infotablet.reminders.Agenda;
import ba.infotablet.reminders.ReminderText;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.TrackingSpan;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.BsDate;
import ba.infotablet.util.Fmt;

/**
 * "Podsjetnici" card on Početna: missed and upcoming reminders of the next seven days, as many
 * rows as fit. Rows on the day named in the header show only the time; later ones show the day.
 * A snoozed reminder shows when it rings again (amber, with a bell). Tapping the card opens the
 * Podsjetnici screen.
 */
public final class RemindersPanel extends LinearLayout {

    private static final int ROW_DP = 45;

    private final Palette p;
    private final LinearLayout head;
    private final TextView day;
    private final LinearLayout rows;
    private List<Agenda.Entry> entries = Collections.emptyList();
    private long now;
    private int capacity = 3;

    public RemindersPanel(Context c, Palette p, Runnable onOpen) {
        super(c);
        this.p = p;
        setOrientation(VERTICAL);
        setBackgroundDrawable(Ui.panel(c, p));
        setPadding(Ui.dp(c, 16), Ui.dp(c, 13), Ui.dp(c, 16), Ui.dp(c, 6));
        setClickable(true);
        setOnClickListener(v -> onOpen.run());

        head = new LinearLayout(c);
        head.addView(Ui.label(c, p, "Podsjetnici"), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        day = Ui.label(c, p, "");
        head.addView(day);
        LayoutParams hl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hl.bottomMargin = Ui.dp(c, 4);
        addView(head, hl);

        rows = new LinearLayout(c);
        rows.setOrientation(VERTICAL);
        addView(rows, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public void bind(List<Agenda.Entry> entries, long now) {
        this.entries = entries;
        this.now = now;
        render();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Children are measured but not laid out yet, so work from the measured header height.
        int avail = h - getPaddingTop() - getPaddingBottom() - head.getMeasuredHeight() - Ui.dp(getContext(), 4);
        int fit = Math.max(1, (avail + 1) / Ui.dp(getContext(), ROW_DP));
        if (fit != capacity) {
            capacity = fit;
            post(this::render); // not during layout
        }
    }

    private void render() {
        Context c = getContext();
        rows.removeAllViews();
        if (entries.isEmpty()) {
            day.setText("");
            TextView t = Ui.text(c, 16, Fonts.regular(), p.fg2);
            t.setText("Nema podsjetnika za ovu sedmicu");
            t.setGravity(Gravity.CENTER);
            rows.addView(t, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
            TextView hint = Ui.text(c, 13.5f, Fonts.regular(), p.fg3);
            hint.setText("Dodirnite da dodate novi");
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, 0, 0, Ui.dp(c, 12));
            rows.addView(hint, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        // The header names the day of the first entry; missed ones are always from today or last night.
        long ref = Math.max(entries.get(0).at, Fmt.startOfDay(now));
        day.setText(TrackingSpan.of(BsDate.capitalize(Fmt.relDay(now, ref)).toUpperCase(Locale.ROOT), 0.08f));
        int n = Math.min(capacity, entries.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) rows.addView(Ui.hairline(c, p.soft), new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
            rows.addView(row(c, entries.get(i), ref), new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, ROW_DP) - 1));
        }
    }

    private LinearLayout row(Context c, Agenda.Entry e, long ref) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        boolean waiting = e.state == Agenda.State.RINGING || e.state == Agenda.State.MISSED;
        boolean snoozed = e.state == Agenda.State.SNOOZED;
        boolean sameDay = Fmt.dayOffset(ref, e.at) == 0 || e.state != Agenda.State.UPCOMING;

        TextView t;
        if (sameDay && e.r.allDay) {
            t = Ui.text(c, 12.5f, Fonts.medium(), p.fg2);
            t.setText(TrackingSpan.of("CIJELI DAN", 0.04f));
            t.setMaxLines(2);
        } else if (sameDay) {
            t = Ui.text(c, 19, Fonts.monoMedium(), waiting ? p.alert : snoozed ? p.warn : p.fg);
            t.setText(NarrowSpan.numeric(Fmt.hhmm(snoozed ? e.r.snoozeUntil : e.at)));
        } else {
            t = Ui.text(c, 13, Fonts.medium(), p.fg2);
            t.setText(TrackingSpan.of(shortDay(e.at).toUpperCase(Locale.ROOT), 0.04f));
        }
        if (!(sameDay && e.r.allDay)) t.setSingleLine(true);
        r.addView(t, new LayoutParams(Ui.dp(c, 58), LayoutParams.WRAP_CONTENT));

        TextView txt = Ui.text(c, 16, Fonts.regular(), p.fg);
        String label = e.r.text;
        if (!sameDay && !e.r.allDay) label = Fmt.hhmm(e.at) + " " + label;
        if (e.state == Agenda.State.MISSED) label = label + " · propušteno";
        txt.setText(label);
        txt.setSingleLine(true);
        txt.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams tl = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = Ui.dp(c, 10);
        tl.rightMargin = Ui.dp(c, 10);
        r.addView(txt, tl);

        int icon = waiting ? R.drawable.ic_warning : snoozed ? R.drawable.ic_bell : ReminderText.icon(e.r);
        r.addView(Ui.icon(c, icon, waiting ? p.alert : snoozed ? p.warn : p.fg2, 20));
        return r;
    }

    /** "sutra", otherwise the short weekday (entries are at most a week ahead). */
    private String shortDay(long at) {
        return Fmt.dayOffset(now, at) == 1 ? "sutra" : Fmt.shortWeekday(at);
    }
}
