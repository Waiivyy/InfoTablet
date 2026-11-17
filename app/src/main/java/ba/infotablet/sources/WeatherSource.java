package ba.infotablet.sources;

import android.content.Context;

import java.util.Locale;

import ba.infotablet.Settings;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/** Open-Meteo forecast for the configured location (default Sarajevo). Every 30 minutes. */
public final class WeatherSource extends BaseSource {

    public static final String ID = "weather";

    @Override public String id() { return ID; }

    @Override public String name() { return "Vrijeme (Open-Meteo)"; }

    @Override public long intervalMs(long now) { return 30 * 60_000L; }

    @Override public long staleAfterMs() { return 3 * 3600_000L; }

    @Override
    public String fetch(Context ctx) throws Exception {
        Settings s = Settings.of(ctx);
        String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,precipitation,is_day"
                        + "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,snowfall,uv_index,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,precipitation_sum,snowfall_sum,uv_index_max"
                        + "&timezone=Europe%%2FSarajevo&timeformat=unixtime&forecast_days=7&forecast_hours=25",
                s.latitude(), s.longitude());
        return WeatherParser.parse(Http.get(url)).toString();
    }
}
