package ba.infotablet.util;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Sunrise and sunset from the classic almanac formula (accurate to a minute or two). Used only
 * when the Open-Meteo forecast with today's times is not available yet, for example after a long
 * time without internet.
 */
public final class Sun {

    private static final double ZENITH = 90.833; // official: refraction and the sun's radius

    private Sun() {}

    /** {sunrise, sunset} in epoch milliseconds for the local date of t; 0 when the sun does not rise or set. */
    public static long[] times(double lat, double lon, long t) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(t);
        int year = local.get(Calendar.YEAR), month = local.get(Calendar.MONTH), day = local.get(Calendar.DAY_OF_MONTH);
        int n = local.get(Calendar.DAY_OF_YEAR);
        return new long[] {event(lat, lon, year, month, day, n, true), event(lat, lon, year, month, day, n, false)};
    }

    private static long event(double lat, double lon, int year, int month, int day, int n, boolean rise) {
        double lngHour = lon / 15.0;
        double t = n + ((rise ? 6 : 18) - lngHour) / 24.0;
        double m = 0.9856 * t - 3.289;
        double l = norm360(m + 1.916 * sin(m) + 0.020 * sin(2 * m) + 282.634);
        double ra = norm360(Math.toDegrees(Math.atan(0.91764 * tan(l))));
        ra = (ra + Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90) / 15.0;
        double sinDec = 0.39782 * sin(l);
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (cos(ZENITH) - sinDec * sin(lat)) / (cosDec * cos(lat));
        if (cosH > 1 || cosH < -1) return 0;
        double h = (rise ? 360 - Math.toDegrees(Math.acos(cosH)) : Math.toDegrees(Math.acos(cosH))) / 15.0;
        double localMean = h + ra - 0.06571 * t - 6.622;
        double ut = ((localMean - lngHour) % 24 + 24) % 24;
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(year, month, day);
        return utc.getTimeInMillis() + Math.round(ut * 3600_000.0);
    }

    private static double sin(double deg) {
        return Math.sin(Math.toRadians(deg));
    }

    private static double cos(double deg) {
        return Math.cos(Math.toRadians(deg));
    }

    private static double tan(double deg) {
        return Math.tan(Math.toRadians(deg));
    }

    private static double norm360(double d) {
        return ((d % 360) + 360) % 360;
    }
}
