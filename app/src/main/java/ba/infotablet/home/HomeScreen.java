package ba.infotablet.home;

import android.content.Context;
import android.widget.FrameLayout;

import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.sources.WarningsSource;
import ba.infotablet.sources.WeatherSource;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.SpanGrid;
import ba.infotablet.ui.Ui;

/** Početna: clock and date, weather, optional warning banner, reminders, fuel and the top headline. */
public final class HomeScreen extends FrameLayout implements Refreshable {

    private final ClockPanel clock;
    private final WeatherPanel weather;
    private final WarningBanner banner;
    private final long weatherStaleMs = new WeatherSource().staleAfterMs();

    public HomeScreen(Context c, Palette p) {
        super(c);
        SpanGrid grid = new SpanGrid(c, 3, Ui.dp(c, 10));
        int pad = Ui.dp(c, 12);
        grid.setPadding(pad, pad, pad, pad);
        grid.setRows(Ui.dp(c, 240), Ui.dp(c, 46), -1);

        clock = new ClockPanel(c, p);
        weather = new WeatherPanel(c, p);
        banner = new WarningBanner(c, p);
        grid.add(clock, 0, 0, 2, 1);
        grid.add(weather, 2, 0, 1, 1);
        grid.add(banner, 0, 1, 3, 1);
        grid.add(new PlaceholderPanel(c, p, "Podsjetnici", "Uskoro"), 0, 2, 1, 1);
        grid.add(new PlaceholderPanel(c, p, "Gorivo danas", "Uskoro"), 1, 2, 1, 1);
        grid.add(new PlaceholderPanel(c, p, "Vijesti", "Uskoro"), 2, 2, 1, 1);

        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public void refresh(long now) {
        Context c = getContext();
        clock.update();
        weather.bind(Cache.get(c, WeatherSource.ID), weatherStaleMs, Settings.of(c).locationName(), now);
        banner.bind(Cache.get(c, WarningsSource.ID), now);
    }
}
