package ba.infotablet;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Device-local settings (SharedPreferences "settings"). Personal values (names, links, streets) are
 * entered on the device only and never ship in the code. Every getter has its default here.
 */
public final class Settings {

    /** Početna tiles for the bottom row (three slots). */
    public static final String[] TILES = {"reminders", "fuel", "news", "city2", "rates", "air"};
    public static final String DEFAULT_TILES = "reminders,fuel,news";
    /** Tabs that can be hidden (Početna and Postavke always stay). */
    public static final int FIRST_HIDEABLE_TAB = 1, LAST_HIDEABLE_TAB = 7;
    /** News categories that can be hidden; crime news are hidden by default. */
    public static final String[] NEWS_CATEGORIES = {"crna", "sport", "ekonomija", "svijet", "kultura", "tech"};

    /** Keys that may be set from adb (see ConfigReceiver); personal values never ship in the code. */
    public static final String[] ADB_KEYS = {"sport_club", "sport_league", "football_key", "creator_name", "creator_link", "water_places", "city", "city2"};

    /** Keys kept by "Vrati zadane postavke": the PIN and the values set during installation. */
    private static final List<String> KEPT_ON_RESET = Arrays.asList("pin_hash", "creator_name", "creator_link");

    private final SharedPreferences prefs;

    private Settings(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static Settings of(Context ctx) {
        return new Settings(ctx);
    }

    // ---- Location ----

    public Cities.City city() {
        return Cities.home(prefs.getString("city", "Sarajevo"));
    }

    public double latitude() {
        return city().lat;
    }

    public double longitude() {
        return city().lon;
    }

    public String locationName() {
        return city().name;
    }

    /** MeteoAlarm region shown at every level (others only orange and red). */
    public String warningRegion() {
        return city().region;
    }

    /** The second-city weather tile, or null when none is chosen. */
    public Cities.City city2() {
        return Cities.other(prefs.getString("city2", ""));
    }

    // ---- Look ----

    /** "auto" (light from sunrise, dark from sunset), "light" or "dark". */
    public String theme() {
        return prefs.getString("theme", "auto");
    }

    /** "kobalt", "petrol", "ljubicasta" or "grafit". */
    public String accent() {
        return prefs.getString("accent", "kobalt");
    }

    /** Text size: 0 normal, 1 "Veća", 2 "Najveća". */
    public int textSize() {
        return prefs.getBoolean("huge_text", false) ? 2 : prefs.getBoolean("large_text", false) ? 1 : 0;
    }

    public void setTextSize(int size) {
        prefs.edit().putBoolean("large_text", size == 1).putBoolean("huge_text", size == 2).apply();
    }

    public boolean compact() {
        return prefs.getBoolean("compact", false);
    }

    /** "standard", "contrast" or "grid". */
    public String background() {
        return prefs.getString("background", "standard");
    }

    public boolean clock24h() {
        return prefs.getBoolean("clock_24h", true);
    }

    public boolean clockSeconds() {
        return prefs.getBoolean("clock_seconds", false);
    }

    // ---- Početna and tabs ----

    /** The three bottom-row tiles, left to right. */
    public String[] tiles() {
        String[] t = prefs.getString("tiles", DEFAULT_TILES).split(",");
        return t.length == 3 ? t : DEFAULT_TILES.split(",");
    }

    public void setTile(int slot, String tile) {
        String[] t = tiles();
        t[slot] = tile;
        putString("tiles", t[0] + "," + t[1] + "," + t[2]);
    }

    public boolean tabVisible(int tab) {
        return tab < FIRST_HIDEABLE_TAB || tab > LAST_HIDEABLE_TAB || !prefs.getBoolean("tab_hidden_" + tab, false);
    }

    public void setTabVisible(int tab, boolean visible) {
        prefs.edit().putBoolean("tab_hidden_" + tab, !visible).apply();
    }

    // ---- Night ----

    public boolean nightDim() {
        return prefs.getBoolean("night_dim", true);
    }

    /** Screen brightness at night, in percent (5 to 60). */
    public int nightBrightness() {
        return prefs.getInt("night_brightness", 15);
    }

    /** Dim from sunset to sunrise (true) or between nightStart() and nightEnd(). */
    public boolean nightBySun() {
        return prefs.getBoolean("night_by_sun", true);
    }

    /** Minutes after midnight. */
    public int nightStart() {
        return prefs.getInt("night_start", 22 * 60);
    }

    public int nightEnd() {
        return prefs.getInt("night_end", 6 * 60);
    }

    // ---- Content ----

    /** Per-source on/off switch (all sources on by default). */
    public boolean sourceEnabled(String sourceId) {
        return prefs.getBoolean("source_on_" + sourceId, true);
    }

    public void setSourceEnabled(String sourceId, boolean on) {
        prefs.edit().putBoolean("source_on_" + sourceId, on).apply();
    }

    /** How much less often sources refresh: 1 (normal), 2 or 4. */
    public int refreshFactor() {
        int f = prefs.getInt("refresh_factor", 1);
        return f == 2 || f == 4 ? f : 1;
    }

    /** Club for the Sport screen: nfsbih team id ("409") or name ("FK Sarajevo"); empty until chosen. */
    public String sportClub() {
        return prefs.getString("sport_club", "");
    }

    /** Streets and neighbourhoods for water, power, heating and gas notices (comma separated). */
    public String streets() {
        return prefs.getString("water_places", "");
    }

    public boolean hasStreets() {
        return !streets().trim().isEmpty();
    }

    /** League of the followed club: "bih" (nfsbih.ba, the default), "PL", "BL1" or "PD" (football-data.org). */
    public String sportLeague() {
        String l = prefs.getString("sport_league", "bih");
        return l.equals("PL") || l.equals("BL1") || l.equals("PD") ? l : "bih";
    }

    public void setSportClub(String league, String club) {
        prefs.edit().putString("sport_league", league).putString("sport_club", club).apply();
    }

    /** football-data.org API key, set over adb only; never exported in backups. */
    public String footballKey() {
        return prefs.getString("football_key", "").trim();
    }

    /** Currencies shown in Korisno and on the rates tile, in order. */
    public List<String> currencies() {
        String s = prefs.getString("currencies", "USD,CHF,GBP");
        List<String> out = new ArrayList<String>();
        for (String c : s.split(",")) if (c.trim().length() == 3) out.add(c.trim());
        return out;
    }

    public boolean newsCategoryHidden(String cat) {
        return Arrays.asList(prefs.getString("news_hidden", "crna").split(",")).contains(cat);
    }

    public void setNewsCategoryHidden(String cat, boolean hidden) {
        List<String> list = new ArrayList<String>();
        for (String c : NEWS_CATEGORIES) {
            boolean h = c.equals(cat) ? hidden : newsCategoryHidden(c);
            if (h) list.add(c);
        }
        putString("news_hidden", join(list));
    }

    /** Hide "crna hronika" (crime) news; on by default. */
    public boolean hideCrime() {
        return newsCategoryHidden("crna");
    }

    // ---- PIN and Wi-Fi page ----

    /** PIN for Postavke and the Wi-Fi page, stored only as a salted hash ("" when not set yet). */
    public String pinHash() {
        return prefs.getString("pin_hash", "");
    }

    public boolean hasPin() {
        return !pinHash().isEmpty();
    }

    public void setPinHash(String hash) {
        prefs.edit().putString("pin_hash", hash == null ? "" : hash).apply();
    }

    /** The local Wi-Fi page for adding reminders from a phone; on by default (it only runs once a PIN is set). */
    public boolean webEnabled() {
        return prefs.getBoolean("web_enabled", true);
    }

    public void setWebEnabled(boolean on) {
        prefs.edit().putBoolean("web_enabled", on).apply();
    }

    // ---- Generic access, backup and reset ----

    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public String getString(String key) {
        return prefs.getString(key, "");
    }

    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public void putInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    /** Every stored value, for the backup file. */
    public Map<String, ?> all() {
        return prefs.getAll();
    }

    /** Puts back values from a backup (strings, booleans and whole numbers only). */
    public void restore(Map<String, Object> values) {
        SharedPreferences.Editor e = prefs.edit();
        for (Map.Entry<String, Object> v : values.entrySet()) {
            Object o = v.getValue();
            if (o instanceof Boolean) e.putBoolean(v.getKey(), (Boolean) o);
            else if (o instanceof Integer) e.putInt(v.getKey(), (Integer) o);
            else if (o instanceof Long) e.putLong(v.getKey(), (Long) o);
            else if (o instanceof String) e.putString(v.getKey(), (String) o);
        }
        e.apply();
    }

    /** Back to defaults, keeping the PIN and the values set during installation. */
    public void reset() {
        SharedPreferences.Editor e = prefs.edit();
        for (String k : prefs.getAll().keySet()) if (!KEPT_ON_RESET.contains(k)) e.remove(k);
        e.apply();
    }

    private static String join(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String s : list) {
            if (b.length() > 0) b.append(',');
            b.append(s);
        }
        return b.toString();
    }
}
