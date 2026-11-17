package ba.infotablet.sources;

import android.content.Context;

import java.util.Locale;

import ba.infotablet.Settings;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/** CAMS Europe air quality and pollen forecast via Open-Meteo. Every 60 minutes. */
public final class AirCamsSource extends BaseSource {

    public static final String ID = "air_cams";

    @Override public String id() { return ID; }

    @Override public String name() { return "Zrak, prognoza i polen (CAMS)"; }

    @Override public long intervalMs(long now) { return 60 * 60_000L; }

    @Override public long staleAfterMs() { return 4 * 3600_000L; }

    @Override
    public String fetch(Context ctx) throws Exception {
        Settings s = Settings.of(ctx);
        String url = String.format(Locale.ROOT,
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f"
                        + "&current=european_aqi,pm10,pm2_5,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"
                        + "&hourly=european_aqi&timezone=Europe%%2FSarajevo&timeformat=unixtime&forecast_hours=48&domains=cams_europe",
                s.latitude(), s.longitude());
        return AirParsers.cams(Http.get(url)).toString();
    }
}
