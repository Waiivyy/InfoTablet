package ba.infotablet.home;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

import ba.infotablet.calendar.Holidays;
import ba.infotablet.ui.FontSpan;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.BsDate;
import ba.infotablet.util.Clock;

/** The large clock and date on Početna. Updated once a minute by the activity (ACTION_TIME_TICK). */
public final class ClockPanel extends LinearLayout {

    private final Palette p;
    private final TextView time;
    private final TextView date;
    private final LinearLayout nextRow;
    private final TextView nextText;
    private String shownNext = null;
    private String shownTime = "";
    private String shownDate = "";

    public ClockPanel(Context c, Palette p) {
        super(c);
        this.p = p;
        setOrientation(VERTICAL);
        setBackgroundDrawable(Ui.panel(c, p));
        setPadding(Ui.dp(c, 22), Ui.dp(c, 12), Ui.dp(c, 22), Ui.dp(c, 12));

        time = Ui.text(c, 150, Fonts.monoLight(), p.fg);
        time.setSingleLine(true);
        time.setGravity(Gravity.START);
        LayoutParams tl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tl.leftMargin = -Ui.dp(c, 5); // optical alignment of the large digits with the date below
        Ui.trimDigits(time, tl);
        addView(time, tl);

        date = Ui.text(c, 28, Fonts.regular(), p.fg);
        date.setSingleLine(true);
        LayoutParams dl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        dl.topMargin = Ui.dp(c, 14);
        addView(date, dl);

        View spacer = new View(c);
        addView(spacer, new LayoutParams(1, 0, 1f));

        // Next holiday, as in the mockup: "Dan državnosti · srijeda, 25. novembar · neradni dan za 8 dana".
        nextRow = new LinearLayout(c);
        nextRow.setOrientation(VERTICAL);
        nextRow.addView(Ui.hairline(c, p.soft), new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
        LinearLayout line = new LinearLayout(c);
        line.setGravity(android.view.Gravity.CENTER_VERTICAL);
        line.setPadding(0, Ui.dp(c, 10), 0, 0);
        line.addView(Ui.icon(c, ba.infotablet.R.drawable.ic_calendar, p.accent, 20));
        nextText = Ui.text(c, 15.5f, Fonts.regular(), p.fg2);
        nextText.setSingleLine(true);
        LayoutParams nl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        nl.leftMargin = Ui.dp(c, 8);
        line.addView(nextText, nl);
        nextRow.addView(line);
        nextRow.setVisibility(GONE);
        addView(nextRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        update();
    }

    public void update() {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(Clock.now());
        String t = BsDate.hhmm(now);
        String d = BsDate.longDate(now);
        if (!t.equals(shownTime)) {
            time.setText(NarrowSpan.numeric(t));
            shownTime = t;
        }
        if (!d.equals(shownDate)) {
            date.setText(d);
            shownDate = d;
        }
        Holidays.Day h = Holidays.next(Holidays.all(getContext()), now.getTimeInMillis(), 30);
        String key = h == null ? "" : h.name + h.date + Holidays.daysUntil(now.getTimeInMillis(), h.date);
        if (!key.equals(shownNext)) {
            shownNext = key;
            if (h == null) {
                nextRow.setVisibility(GONE);
            } else {
                Calendar hc = Calendar.getInstance();
                hc.setTimeInMillis(h.date);
                android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(h.name);
                b.setSpan(new FontSpan(Fonts.medium()), 0, b.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                b.setSpan(new android.text.style.ForegroundColorSpan(p.fg), 0, b.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                b.append(" · ").append(BsDate.weekday(hc)).append(", ").append(String.valueOf(hc.get(Calendar.DAY_OF_MONTH))).append(". ").append(BsDate.month(hc))
                        .append(" · ").append(h.state ? "neradni dan " : "vjerski praznik ")
                        .append(Holidays.countdown(Holidays.daysUntil(now.getTimeInMillis(), h.date)));
                nextText.setText(b);
                nextRow.setVisibility(VISIBLE);
            }
        }
    }
}
