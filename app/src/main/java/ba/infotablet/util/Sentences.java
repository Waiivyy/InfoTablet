package ba.infotablet.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits Bosnian news text into sentences without breaking on initials ("M. O."), numbers
 * ("u 69. minuti", "14. novembra"), "d.o.o." or common abbreviations.
 */
public final class Sentences {

    /** Candidate boundary: end punctuation, optional closing quote, space, then an uppercase start. */
    private static final Pattern BOUNDARY = Pattern.compile("[.!?…][\"”“»]?\\s+(?=[\"„“«]?[A-ZČĆŽŠĐ0-9])");
    private static final Pattern INITIALS = Pattern.compile("(?:^|\\s)(?:[A-Za-zČĆŽŠĐčćžšđ]\\.)+$");
    private static final Pattern NUMBER = Pattern.compile("(?:^|\\s)\\d{1,4}\\.$");
    private static final Set<String> ABBREV = new HashSet<String>();

    static {
        for (String a : new String[] {"d.o.o", "d.d", "tzv", "npr", "dr", "mr", "prof", "sl", "ul", "br", "str",
                "god", "mil", "mlrd", "tj", "itd", "sv", "st", "gosp", "ing", "doc", "akad", "prim"}) {
            ABBREV.add(a);
        }
    }

    private Sentences() {}

    public static List<String> split(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        String t = text.replaceAll("\\s+", " ").trim();
        int start = 0;
        Matcher m = BOUNDARY.matcher(t);
        while (m.find()) {
            String before = t.substring(start, m.start() + 1);
            if (isAbbreviation(before)) continue;
            String s = t.substring(start, m.end()).trim();
            if (!s.isEmpty()) out.add(s);
            start = m.end();
        }
        String rest = t.substring(start).trim();
        if (!rest.isEmpty()) out.add(rest);
        return out;
    }

    /** Up to {@code max} sentences, stopping before {@code maxChars} (always at least one sentence). */
    public static String first(String text, int max, int maxChars) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String s : split(text)) {
            if (n >= max) break;
            if (n > 0 && b.length() + 1 + s.length() > maxChars) break;
            if (b.length() > 0) b.append(' ');
            b.append(s);
            n++;
        }
        return b.toString();
    }

    private static boolean isAbbreviation(String before) {
        if (!before.endsWith(".")) return false;
        if (NUMBER.matcher(before).find() || INITIALS.matcher(before).find()) return true;
        int sp = before.lastIndexOf(' ');
        String word = before.substring(sp + 1, before.length() - 1).toLowerCase(Locale.ROOT);
        return ABBREV.contains(word);
    }
}
