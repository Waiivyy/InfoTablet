package ba.infotablet.sources;

import android.content.Context;
import android.util.Xml;

import ba.infotablet.Settings;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/** Sarajevogas works notices from the site's RSS feed. Every 3 hours. */
public final class GasSource extends BaseSource {

    public static final String ID = "gas";

    @Override public String id() { return ID; }

    @Override public String name() { return "Plin (Sarajevogas)"; }

    @Override public boolean active(Context ctx) { return Settings.of(ctx).hasStreets(); }

    @Override public long intervalMs(long now) { return 3 * 3600_000L; }

    @Override public long staleAfterMs() { return 24 * 3600_000L; }

    @Override
    public String fetch(Context ctx) throws Exception {
        return UtilityParsers.gas(NewsParser.parseFeed(Xml.newPullParser(), Http.get("https://www.sarajevogas.ba/feed/", 512 * 1024))).toString();
    }
}
