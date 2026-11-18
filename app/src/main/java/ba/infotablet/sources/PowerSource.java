package ba.infotablet.sources;

import android.content.Context;

import ba.infotablet.Settings;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/** Planned power cuts in the Sarajevo area (Elektroprivreda BiH), announced 2 to 3 days ahead. Every 3 hours. */
public final class PowerSource extends BaseSource {

    public static final String ID = "power";

    @Override public String id() { return ID; }

    @Override public String name() { return "Struja (Elektroprivreda BiH)"; }

    @Override public boolean active(Context ctx) { return Settings.of(ctx).hasStreets(); }

    @Override public long intervalMs(long now) { return 3 * 3600_000L; }

    @Override public long staleAfterMs() { return 12 * 3600_000L; }

    @Override
    public String fetch(Context ctx) throws Exception {
        // One page for all of the Federation (about 270 KB); only the Sarajevo rows are kept.
        return UtilityParsers.power(Http.get("https://www.epbih.ba/stranica/servisne-informacije", 1024 * 1024)).toString();
    }
}
