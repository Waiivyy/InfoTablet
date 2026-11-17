package ba.infotablet;

/**
 * Built-in places, so choosing a city needs no lookup service. The home location is one of the ten
 * BiH cities that give their name to a MeteoAlarm warning region; the second-city tile can be any
 * of the list (cities where family often lives).
 */
public final class Cities {

    public static final class City {
        public final String name;
        public final double lat, lon;
        /** MeteoAlarm region for BiH cities, "" elsewhere. */
        public final String region;

        City(String name, double lat, double lon, String region) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.region = region;
        }
    }

    /** Home locations: the region capitals of the MeteoAlarm BiH regions (docs/sources.md). */
    public static final City[] HOME = {
            new City("Sarajevo", 43.8563, 18.4131, "BA006"),
            new City("Banja Luka", 44.7722, 17.1910, "BA003"),
            new City("Bihać", 44.8169, 15.8708, "BA001"),
            new City("Foča", 43.5065, 18.7766, "BA009"),
            new City("Livno", 43.8269, 17.0075, "BA005"),
            new City("Mostar", 43.3438, 17.8078, "BA008"),
            new City("Prijedor", 44.9799, 16.7139, "BA002"),
            new City("Trebinje", 42.7117, 18.3436, "BA010"),
            new City("Tuzla", 44.5384, 18.6763, "BA004"),
            new City("Višegrad", 43.7825, 19.2925, "BA007"),
    };

    /** Second-city choices, by country then name. */
    public static final City[] OTHER = {
            new City("Beč", 48.2082, 16.3738, ""),
            new City("Graz", 47.0707, 15.4395, ""),
            new City("Linz", 48.3069, 14.2858, ""),
            new City("Salzburg", 47.8095, 13.0550, ""),
            new City("Berlin", 52.5200, 13.4050, ""),
            new City("Düsseldorf", 51.2277, 6.7735, ""),
            new City("Frankfurt", 50.1109, 8.6821, ""),
            new City("Hamburg", 53.5511, 9.9937, ""),
            new City("Köln", 50.9375, 6.9603, ""),
            new City("München", 48.1374, 11.5755, ""),
            new City("Stuttgart", 48.7758, 9.1829, ""),
            new City("Zürich", 47.3769, 8.5417, ""),
            new City("Zagreb", 45.8150, 15.9819, ""),
            new City("Split", 43.5081, 16.4402, ""),
            new City("Dubrovnik", 42.6507, 18.0944, ""),
            new City("Beograd", 44.8125, 20.4612, ""),
            new City("Podgorica", 42.4304, 19.2594, ""),
            new City("Ljubljana", 46.0569, 14.5058, ""),
            new City("Istanbul", 41.0082, 28.9784, ""),
            new City("Rim", 41.9028, 12.4964, ""),
            new City("Milano", 45.4642, 9.1900, ""),
            new City("Pariz", 48.8566, 2.3522, ""),
            new City("Amsterdam", 52.3676, 4.9041, ""),
            new City("Brisel", 50.8503, 4.3517, ""),
            new City("London", 51.5074, -0.1278, ""),
            new City("Dublin", 53.3498, -6.2603, ""),
            new City("Kopenhagen", 55.6761, 12.5683, ""),
            new City("Malmö", 55.6050, 13.0038, ""),
            new City("Göteborg", 57.7089, 11.9746, ""),
            new City("Stockholm", 59.3293, 18.0686, ""),
            new City("Oslo", 59.9139, 10.7522, ""),
            new City("New York", 40.7128, -74.0060, ""),
            new City("Chicago", 41.8781, -87.6298, ""),
            new City("Toronto", 43.6532, -79.3832, ""),
            new City("Sydney", -33.8688, 151.2093, ""),
            new City("Banja Luka", 44.7722, 17.1910, ""),
            new City("Mostar", 43.3438, 17.8078, ""),
            new City("Tuzla", 44.5384, 18.6763, ""),
            new City("Zenica", 44.2034, 17.9078, ""),
    };

    private Cities() {}

    public static City home(String name) {
        for (City c : HOME) if (c.name.equals(name)) return c;
        return HOME[0];
    }

    /** The second city by name, or null when none is chosen. */
    public static City other(String name) {
        if (name == null || name.isEmpty()) return null;
        for (City c : OTHER) if (c.name.equals(name)) return c;
        return null;
    }
}
