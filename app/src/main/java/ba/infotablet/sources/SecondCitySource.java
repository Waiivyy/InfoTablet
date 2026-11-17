package ba.infotablet.sources;

import android.content.Context;

import java.util.Locale;

import ba.infotablet.Cities;
import ba.infotablet.Settings;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/** Weather for the optional second city (Open-Meteo, one small request an hour). Idle when none is chosen. */
public final class SecondCitySource extends BaseSource {

    public static final String ID = "weather2";

    @Override public String id() { return ID; }

    @Override public String name() { return "Drugi grad (Open-Meteo)"; }

    @Override public long intervalMs(long now) { return 60 * 60_000L; }

    @Override public long staleAfterMs() { return 4 * 3600_000L; }

    @Override
    public boolean active(Context ctx) {
        return Settings.of(ctx).city2() != null;
    }

    @Override
    public String fetch(Context ctx) throws Exception {
        Cities.City c = Settings.of(ctx).city2();
        if (c == null) throw new IllegalStateException("no second city");
        String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto&timeformat=unixtime&forecast_days=1",
                c.lat, c.lon);
        // Remember which city the payload belongs to, so a change of city never shows old numbers.
        return WeatherParser.secondCity(Http.get(url)).put("city", c.name).toString();
    }
}
