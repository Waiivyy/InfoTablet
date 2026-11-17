package ba.infotablet.ui;

/** European Air Quality Index levels (EEA), 0 good to 5 extremely poor, with the EEA colours. */
public final class AirLevel {

    private static final String[] NAMES = {"Dobar", "Prihvatljiv", "Umjeren", "Loš", "Vrlo loš", "Izuzetno loš"};
    private static final int[] COLORS = {0xFF50F0E6, 0xFF50CCAA, 0xFFF0E641, 0xFFFF5050, 0xFF960032, 0xFF7D2181};

    private AirLevel() {}

    public static boolean valid(int level) {
        return level >= 0 && level < NAMES.length;
    }

    public static String name(int level) {
        return valid(level) ? NAMES[level] : "Nema podataka";
    }

    public static int color(int level) {
        return valid(level) ? COLORS[level] : 0xFF9AA3AC;
    }

    /** Readable text colour on top of {@link #color}. */
    public static int onColor(int level) {
        return level >= 3 ? 0xFFFFFFFF : 0xFF14181C;
    }

    /** Open-Meteo european_aqi value (0 to 100+) to a level. */
    public static int fromAqi(double aqi) {
        if (Double.isNaN(aqi)) return -1;
        if (aqi <= 20) return 0;
        if (aqi <= 40) return 1;
        if (aqi <= 60) return 2;
        if (aqi <= 80) return 3;
        if (aqi <= 100) return 4;
        return 5;
    }
}
