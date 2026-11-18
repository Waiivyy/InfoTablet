package ba.infotablet.sources;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ba.infotablet.util.Iso;
import ba.infotablet.util.Sentences;

/** Feed (RSS 2.0 or Atom) and article parsing for the three news portals. */
public final class NewsParser {

    /** One feed entry before the article text is fetched. */
    public static final class Item {
        public String title = "", link = "", summary = "";
        /** Full text (WordPress content:encoded), capped; empty for most feeds. */
        public String content = "";
        public long time;
        public final List<String> categories = new ArrayList<String>();
    }

    public enum Portal {
        KLIX("klix.ba", "https://www.klix.ba/rss", "id=\"text\"", 12_000, 320_000,
                "div#excerpt span.lead", "div#text > p"),
        N1("n1info.ba", "https://n1info.ba/feed/", "article-content-wrapper-1", 4_000, 220_000,
                "p[data-testid=article-lead-text]", "div[data-testid^=article-content-wrapper] > div.rich-text-block > p"),
        RADIOSARAJEVO("radiosarajevo.ba", "https://radiosarajevo.ba/rss", "text-editor-article", 14_000, 180_000,
                null, "div.text-editor-article > p");

        public final String label, feed, marker, leadSelector, bodySelector;
        public final int extraBytes, cap;

        Portal(String label, String feed, String marker, int extraBytes, int cap, String leadSelector, String bodySelector) {
            this.label = label;
            this.feed = feed;
            this.marker = marker;
            this.extraBytes = extraBytes;
            this.cap = cap;
            this.leadSelector = leadSelector;
            this.bodySelector = bodySelector;
        }
    }

    private NewsParser() {}

    /** Parses RSS {@code <item>} or Atom {@code <entry>} elements; the parser is supplied by the caller. */
    public static List<Item> parseFeed(XmlPullParser x, String xml) throws XmlPullParserException, IOException {
        x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        x.setInput(new StringReader(xml));
        List<Item> items = new ArrayList<Item>();
        Item cur = null;
        for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
            if (e == XmlPullParser.START_TAG) {
                String n = x.getName();
                if ("item".equals(n) || "entry".equals(n)) {
                    cur = new Item();
                } else if (cur != null) {
                    if ("title".equals(n)) {
                        cur.title = clean(x.nextText());
                    } else if ("link".equals(n)) {
                        String href = x.getAttributeValue(null, "href");
                        String rel = x.getAttributeValue(null, "rel");
                        if (href != null) {
                            if (rel == null || "alternate".equals(rel)) cur.link = href.trim();
                        } else {
                            cur.link = x.nextText().trim();
                        }
                    } else if ("pubDate".equals(n) || "updated".equals(n) || "published".equals(n)) {
                        long t = date(x.nextText());
                        if (t > 0 && cur.time == 0) cur.time = t;
                    } else if ("description".equals(n) || "summary".equals(n)) {
                        cur.summary = clean(Jsoup.parse(x.nextText()).text());
                    } else if ("content:encoded".equals(n)) {
                        String t = clean(Jsoup.parse(x.nextText()).text());
                        cur.content = t.length() > 4000 ? t.substring(0, 4000) : t;
                    } else if ("category".equals(n)) {
                        String term = x.getAttributeValue(null, "term");
                        cur.categories.add(clean(term != null ? term : x.nextText()));
                    }
                }
            } else if (e == XmlPullParser.END_TAG && cur != null && ("item".equals(x.getName()) || "entry".equals(x.getName()))) {
                if (!cur.link.isEmpty() && !cur.title.isEmpty()) items.add(cur);
                cur = null;
            }
        }
        return items;
    }

    /** RFC 822 ("Mon, 16 Nov 2026 23:25:00 +0100", also used by radiosarajevo.ba's Atom) or ISO 8601. */
    static long date(String s) {
        if (s == null) return 0;
        s = s.trim();
        try {
            return new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(s).getTime();
        } catch (ParseException ignored) {
            return Iso.parse(s);
        }
    }

    /** Feeds carry literal entities such as &amp;quot; inside CDATA. */
    static String clean(String s) {
        return s == null ? "" : Parser.unescapeEntities(s, false).replaceAll("\\s+", " ").trim();
    }

    /** True for items without readable text (video, radio, photo galleries). */
    public static boolean skip(String link) {
        String p = path(link);
        return p.startsWith("/video/") || p.contains("/video/") || p.startsWith("/radio/") || p.startsWith("/multimedia/") || p.startsWith("/foto/");
    }

    public static boolean isCrime(String link, List<String> categories) {
        if (path(link).contains("/crna-hronika/")) return true;
        for (String c : categories) if ("crna hronika".equalsIgnoreCase(c)) return true;
        return false;
    }

    /**
     * Crime and fatal accidents that portals file outside their crime section, recognised by words
     * in the headline and lead (Bosnian, without diacritics). Used only to hide such items when
     * "Crna hronika" is switched off, so it errs on the side of hiding.
     */
    private static final java.util.regex.Pattern CRIME_WORDS = java.util.regex.Pattern.compile(
            "\\b(ubi(o|la|li|jen|jena|jeni|jeno|stvo|stva|stvu|ce|ca|ju)|ubist|poginu|smrtno strada|stradao|stradala|"
                    + "pucnjav|upucan|izboden|izbo(o|la|li)|nozem|silova|pljack|razbojni|lesh?\\b|bezivotn|tijelo (je )?pronad|"
                    + "zlostavlj|pedofil|otmic|ote(o|la|li)\\b|femicid|samoubist|uhapsen|uhapsi)");

    public static boolean soundsLikeCrime(String text) {
        if (text == null || text.isEmpty()) return false;
        String t = text.toLowerCase(Locale.ROOT).replace('č', 'c').replace('ć', 'c').replace('š', 's').replace('ž', 'z').replace("đ", "dj");
        return CRIME_WORDS.matcher(t).find();
    }

    /** App categories: crna, sport, ekonomija, sarajevo, bih, svijet, kultura, tech, ostalo. */
    public static String category(String link, List<String> categories) {
        if (isCrime(link, categories)) return "crna";
        String p = path(link);
        if (p.startsWith("/sport")) return "sport";
        if (p.startsWith("/biznis") || p.contains("/ekonomija")) return "ekonomija";
        if (p.contains("/sarajevo") || p.contains("/lokalne-teme")) return "sarajevo";
        if (p.contains("/regija") || p.contains("/svijet")) return "svijet";
        if (p.startsWith("/magazin") || p.startsWith("/lifestyle") || p.startsWith("/kultura") || p.startsWith("/showbiz")
                || p.startsWith("/metromahala") || p.startsWith("/zdravlje")) return "kultura";
        if (p.startsWith("/scitech") || p.startsWith("/auto") || p.contains("/tehnologija") || p.contains("/tech")) return "tech";
        if (p.startsWith("/vijesti") || p.startsWith("/bih")) return "bih";
        return "ostalo";
    }

    /** Up to three sentences from the article's own lead and first paragraphs; empty if not found. */
    public static String articleLead(Portal portal, String html) {
        Document d = Jsoup.parse(html);
        StringBuilder text = new StringBuilder();
        if (portal.leadSelector != null) {
            Element lead = d.selectFirst(portal.leadSelector);
            if (lead != null) text.append(lead.text().trim());
        }
        for (Element p : d.select(portal.bodySelector)) {
            String t = p.text().trim();
            if (t.isEmpty()) continue;
            if (text.length() > 0) text.append(' ');
            text.append(t);
            if (text.length() > 1200) break;
        }
        String out = Sentences.first(text.toString(), 3, 350);
        return out.length() < 40 ? "" : out;
    }

    /** First complete sentence of a feed summary (n1 cuts it at 150 characters mid-word). */
    public static String summaryFallback(String summary) {
        List<String> s = Sentences.split(summary);
        if (s.isEmpty()) return "";
        String first = s.get(0);
        boolean complete = first.matches(".*[.!?…][\"”“»]?$");
        return complete ? first : "";
    }

    private static String path(String link) {
        if (link == null) return "";
        String p = link.replaceFirst("^https?://[^/]+", "");
        return p.toLowerCase(Locale.ROOT);
    }
}
