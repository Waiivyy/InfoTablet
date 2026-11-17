package ba.infotablet.home;

import android.content.Context;
import android.widget.FrameLayout;

import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.fuel.FuelPanel;
import ba.infotablet.news.NewsPanel;
import ba.infotablet.reminders.Agenda;
import ba.infotablet.reminders.ReminderStore;
import ba.infotablet.sources.FuelSource;
import ba.infotablet.sources.SmogSource;
import ba.infotablet.sources.WarningsSource;
import ba.infotablet.sources.WeatherSource;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Rail;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.SpanGrid;
import ba.infotablet.ui.Ui;

/** Početna: clock and date, weather, optional warning banner, reminders, fuel and the top headline. */
public final class HomeScreen extends FrameLayout implements Refreshable {

    private final ClockPanel clock;
    private final WeatherPanel weather;
    private final WarningBanner banner;
    private final RemindersPanel reminders;
    private final FuelPanel fuel;
    private final NewsPanel news;
    private final long fuelStaleMs = new FuelSource().staleAfterMs();
    private final long weatherStaleMs = new WeatherSource().staleAfterMs();

    private static final long REMINDER_HORIZON_MS = 7L * 86_400_000L;

    public HomeScreen(Context c, Palette p, Rail.Listener tabs) {
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
        reminders = new RemindersPanel(c, p, () -> tabs.onTab(6));
        grid.add(reminders, 0, 2, 1, 1);
        fuel = new FuelPanel(c, p);
        grid.add(fuel, 1, 2, 1, 1);
        news = new NewsPanel(c, p);
        grid.add(news, 2, 2, 1, 1);

        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public void refresh(long now) {
        Context c = getContext();
        clock.update();
        weather.bind(Cache.get(c, WeatherSource.ID), weatherStaleMs, Settings.of(c).locationName(), now);
        banner.bind(Cache.get(c, WarningsSource.ID), Cache.get(c, SmogSource.ID), now);
        reminders.bind(Agenda.upcoming(ReminderStore.all(c), now, REMINDER_HORIZON_MS), now);
        fuel.bind(Cache.get(c, FuelSource.ID), fuelStaleMs, now);
        news.bind(now);
    }
}
