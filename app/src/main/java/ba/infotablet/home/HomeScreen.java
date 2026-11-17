package ba.infotablet.home;

import android.content.Context;
import android.view.View;
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

/**
 * Početna: clock and date, weather, the warning banner when one is active, and three tiles chosen
 * in Postavke (default: reminders, fuel, top headline). A tile opens its screen when tapped.
 */
public final class HomeScreen extends FrameLayout implements Refreshable {

    private static final long REMINDER_HORIZON_MS = 7L * 86_400_000L;

    private final ClockPanel clock;
    private final WeatherPanel weather;
    private final WarningBanner banner;
    private final View[] tiles = new View[3];
    private final long fuelStaleMs = new FuelSource().staleAfterMs();
    private final long weatherStaleMs = new WeatherSource().staleAfterMs();

    public HomeScreen(Context c, Palette p, Rail.Listener tabs) {
        super(c);
        SpanGrid grid = new SpanGrid(c, 3, Ui.gap(c));
        int pad = Ui.pagePad(c);
        grid.setPadding(pad, pad, pad, pad);
        grid.setRows(Ui.dp(c, 240), Ui.dp(c, 46), -1);

        clock = new ClockPanel(c, p);
        weather = new WeatherPanel(c, p);
        banner = new WarningBanner(c, p);
        grid.add(clock, 0, 0, 2, 1);
        grid.add(weather, 2, 0, 1, 1);
        grid.add(banner, 0, 1, 3, 1);

        Settings s = Settings.of(c);
        String[] chosen = s.tiles();
        for (int i = 0; i < 3; i++) {
            View v = tile(c, p, chosen[i], tabs, s);
            tiles[i] = v;
            grid.add(v, i, 2, 1, 1);
        }
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private static View tile(Context c, Palette p, String key, Rail.Listener tabs, Settings s) {
        View v;
        int tab;
        switch (key) {
            case "fuel": v = new FuelPanel(c, p); tab = 2; break;
            case "news": v = new NewsPanel(c, p); tab = 4; break;
            case "city2": v = new CityTile(c, p); tab = -1; break;
            case "rates": v = new RatesTile(c, p); tab = 7; break;
            case "air": v = new AirTile(c, p); tab = 1; break;
            default: return new RemindersPanel(c, p, () -> tabs.onTab(s.tabVisible(6) ? 6 : 0));
        }
        final int target = tab;
        if (target > 0 && s.tabVisible(target)) {
            v.setClickable(true);
            v.setOnClickListener(x -> tabs.onTab(target));
        }
        return v;
    }

    @Override
    public void refresh(long now) {
        Context c = getContext();
        clock.update();
        weather.bind(Cache.get(c, WeatherSource.ID), weatherStaleMs, Settings.of(c).locationName(), now);
        banner.bind(Cache.get(c, WarningsSource.ID), Cache.get(c, SmogSource.ID), now);
        for (View v : tiles) {
            if (v instanceof RemindersPanel) {
                ((RemindersPanel) v).bind(Agenda.upcoming(ReminderStore.all(c), now, REMINDER_HORIZON_MS), now);
            } else if (v instanceof FuelPanel) {
                ((FuelPanel) v).bind(Cache.get(c, FuelSource.ID), fuelStaleMs, now);
            } else if (v instanceof NewsPanel) {
                ((NewsPanel) v).bind(now);
            } else if (v instanceof Tile) {
                ((Tile) v).bind(now);
            }
        }
    }
}
