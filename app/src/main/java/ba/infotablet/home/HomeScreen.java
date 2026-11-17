package ba.infotablet.home;

import android.content.Context;
import android.widget.FrameLayout;

import ba.infotablet.ui.Palette;
import ba.infotablet.ui.SpanGrid;
import ba.infotablet.ui.Ui;

/** Početna: clock and date, weather, optional warning banner, reminders, fuel and the top headline. */
public final class HomeScreen extends FrameLayout {

    private final ClockPanel clock;

    public HomeScreen(Context c, Palette p) {
        super(c);
        SpanGrid grid = new SpanGrid(c, 3, Ui.dp(c, 10));
        int pad = Ui.dp(c, 12);
        grid.setPadding(pad, pad, pad, pad);
        grid.setRows(Ui.dp(c, 240), Ui.dp(c, 46), -1);

        clock = new ClockPanel(c, p);
        grid.add(clock, 0, 0, 2, 1);
        grid.add(new PlaceholderPanel(c, p, "Sarajevo · sada", "Uskoro"), 2, 0, 1, 1);
        grid.add(new PlaceholderPanel(c, p, "Podsjetnici", "Uskoro"), 0, 2, 1, 1);
        grid.add(new PlaceholderPanel(c, p, "Gorivo danas", "Uskoro"), 1, 2, 1, 1);
        grid.add(new PlaceholderPanel(c, p, "Vijesti", "Uskoro"), 2, 2, 1, 1);

        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void onMinute() {
        clock.update();
    }
}
