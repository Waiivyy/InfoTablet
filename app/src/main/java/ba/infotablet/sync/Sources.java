package ba.infotablet.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ba.infotablet.sources.AirCamsSource;
import ba.infotablet.sources.AirStationsSource;
import ba.infotablet.sources.WarningsSource;
import ba.infotablet.sources.WeatherSource;

/** Every data source of the app, in refresh order. */
public final class Sources {

    private Sources() {}

    static List<BaseSource> all() {
        List<BaseSource> list = new ArrayList<BaseSource>();
        list.add(new WeatherSource());
        list.add(new WarningsSource());
        list.add(new AirStationsSource());
        list.add(new AirCamsSource());
        return Collections.unmodifiableList(list);
    }
}
