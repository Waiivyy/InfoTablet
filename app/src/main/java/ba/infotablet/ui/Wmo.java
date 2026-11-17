package ba.infotablet.ui;

import ba.infotablet.R;

/** WMO weather codes (Open-Meteo) to Bosnian text and an icon. */
public final class Wmo {

    private Wmo() {}

    public static String text(int code) {
        switch (code) {
            case 0: return "Vedro";
            case 1: return "Pretežno vedro";
            case 2: return "Djelimično oblačno";
            case 3: return "Oblačno";
            case 45: return "Magla";
            case 48: return "Magla s injem";
            case 51: return "Slaba rosulja";
            case 53: return "Rosulja";
            case 55: return "Jaka rosulja";
            case 56: case 57: return "Ledena rosulja";
            case 61: return "Slaba kiša";
            case 63: return "Kiša";
            case 65: return "Jaka kiša";
            case 66: case 67: return "Ledena kiša";
            case 71: return "Slab snijeg";
            case 73: return "Snijeg";
            case 75: return "Jak snijeg";
            case 77: return "Snježna zrna";
            case 80: case 81: return "Kišni pljuskovi";
            case 82: return "Jaki pljuskovi";
            case 85: return "Snježni pljuskovi";
            case 86: return "Jaki snježni pljuskovi";
            case 95: return "Grmljavina";
            case 96: case 99: return "Grmljavina s gradom";
            default: return "";
        }
    }

    public static int icon(int code, boolean day) {
        if (code <= 1) return day ? R.drawable.ic_sun : R.drawable.ic_moon;
        if (code == 2) return day ? R.drawable.ic_weather : R.drawable.ic_cloud_moon;
        if (code == 3) return R.drawable.ic_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_fog;
        if (code >= 51 && code <= 57) return R.drawable.ic_drizzle;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return R.drawable.ic_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.ic_snow;
        if (code >= 95) return R.drawable.ic_thunder;
        return R.drawable.ic_cloud;
    }
}
