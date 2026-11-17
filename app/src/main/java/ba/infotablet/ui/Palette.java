package ba.infotablet.ui;

/**
 * Colour tokens from the approved mockups. Light by day, dark by night. Accent presets and the
 * background style derive new palettes from LIGHT and DARK (see Theme).
 */
public final class Palette {

    public final boolean dark;
    /** Draw thin grid lines on the background (background style "Mreža"). */
    public final boolean grid;
    public final int bg, panel, rail, line, soft;
    public final int fg, fg2, fg3;
    public final int accent, accentFg, accentTint;
    public final int ok, warn, alert, warnTint, warnLine;

    private Palette(boolean dark, int bg, int panel, int rail, int line, int soft,
                    int fg, int fg2, int fg3, int accent, int accentFg, int accentTint,
                    int ok, int warn, int alert, int warnTint, int warnLine) {
        this(dark, false, bg, panel, rail, line, soft, fg, fg2, fg3, accent, accentFg, accentTint, ok, warn, alert, warnTint, warnLine);
    }

    private Palette(boolean dark, boolean grid, int bg, int panel, int rail, int line, int soft,
                    int fg, int fg2, int fg3, int accent, int accentFg, int accentTint,
                    int ok, int warn, int alert, int warnTint, int warnLine) {
        this.dark = dark;
        this.grid = grid;
        this.bg = bg;
        this.panel = panel;
        this.rail = rail;
        this.line = line;
        this.soft = soft;
        this.fg = fg;
        this.fg2 = fg2;
        this.fg3 = fg3;
        this.accent = accent;
        this.accentFg = accentFg;
        this.accentTint = accentTint;
        this.ok = ok;
        this.warn = warn;
        this.alert = alert;
        this.warnTint = warnTint;
        this.warnLine = warnLine;
    }

    public static final Palette LIGHT = new Palette(false,
            0xFFE6E9ED, 0xFFFAFBFC, 0xFFDBE0E5, 0xFFCFD6DD, 0xFFE3E7EB,
            0xFF0F1419, 0xFF4D5864, 0xFF66717C,
            0xFF1F46B0, 0xFFFFFFFF, 0x1A1F46B0,
            0xFF1D8A4C, 0xFF965D00, 0xFFC2352A, 0xFFFBF2DE, 0xFFE9CC8F);

    public static final Palette DARK = new Palette(true,
            0xFF080B0E, 0xFF10151A, 0xFF0C1014, 0xFF212931, 0xFF1A2128,
            0xFFCED6DE, 0xFF8A96A2, 0xFF77828E,
            0xFF7C9FFF, 0xFF0A0F1A, 0x217C9FFF,
            0xFF3FB46F, 0xFFE09D36, 0xFFEC695D, 0xFF1C170D, 0xFF4F3D15);

    /** Accent presets: {key, Bosnian name, light accent, dark accent}. Never green, amber or red (status colours). */
    public static final Object[][] ACCENTS = {
            {"kobalt", "Kobalt", 0xFF1F46B0, 0xFF7C9FFF},
            {"petrol", "Petrol", 0xFF0E6B78, 0xFF4FC1CC},
            {"ljubicasta", "Ljubičasta", 0xFF5A3DAA, 0xFFA994FF},
            {"grafit", "Grafit", 0xFF39434F, 0xFFAEB9C5},
    };

    /** The accent colour of a preset for light or dark, for the swatches in Postavke. */
    public static int accentOf(String key, boolean dark) {
        for (Object[] a : ACCENTS) if (a[0].equals(key)) return (Integer) a[dark ? 3 : 2];
        return (Integer) ACCENTS[0][dark ? 3 : 2];
    }

    /** This palette with another accent preset and background style ("standard", "contrast", "grid"). */
    public Palette with(String accentKey, String background) {
        int a = accentOf(accentKey, dark);
        int tint = (a & 0x00FFFFFF) | (dark ? 0x21000000 : 0x1A000000);
        int fgOnAccent = dark ? 0xFF0A0F1A : 0xFFFFFFFF;
        int b = bg, r = rail;
        if ("contrast".equals(background)) {
            b = dark ? 0xFF000000 : 0xFFD2D8DF;
            r = dark ? 0xFF05080A : 0xFFC8CFD7;
        }
        return new Palette(dark, "grid".equals(background), b, panel, r, line, soft, fg, fg2, fg3,
                a, fgOnAccent, tint, ok, warn, alert, warnTint, warnLine);
    }

    /** MeteoAlarm level colours (yellow, orange, red) with readable text on top. */
    public static final int LEVEL_YELLOW = 0xFFF3CF3A;
    public static final int LEVEL_ORANGE = 0xFFF08A24;
    public static final int LEVEL_RED = 0xFFD7332A;
    public static final int LEVEL_TEXT_DARK = 0xFF2B2200;
}
