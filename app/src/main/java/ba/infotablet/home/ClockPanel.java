package ba.infotablet.home;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.BsDate;

/** The large clock and date on Početna. Updated once a minute by the activity (ACTION_TIME_TICK). */
public final class ClockPanel extends LinearLayout {

    private final TextView time;
    private final TextView date;
    private String shownTime = "";
    private String shownDate = "";

    public ClockPanel(Context c, Palette p) {
        super(c);
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

        update();
    }

    public void update() {
        Calendar now = Calendar.getInstance();
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
    }
}
