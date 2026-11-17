package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TimeZone;

import ba.infotablet.util.Iso;

/** BIHAMK road conditions and border crossings: RSS (structured HTML inside) plus the page's update time. */
public final class RoadsParser {

    public static final String BORDER_DEFAULT = "Zadržavanja putničkih vozila nisu duža od 30 minuta.";

    private RoadsParser() {}

    /** {categories:[{name, entries:[{title,text}]}]} from the RSS item description. */
    public static JSONObject rss(XmlPullParser x, String xml) throws XmlPullParserException, IOException, JSONException {
        x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        x.setInput(new StringReader(xml));
        String description = null;
        boolean inItem = false;
        for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
            if (e == XmlPullParser.START_TAG) {
                if ("item".equals(x.getName())) inItem = true;
                else if (inItem && "description".equals(x.getName())) {
                    description = x.nextText();
                    break;
                }
            }
        }
        if (description == null) throw new JSONException("No road report in the feed");
        Document d = Jsoup.parseBodyFragment(description);
        JSONArray cats = new JSONArray();
        for (Element cat : d.select("div.category")) {
            Element h2 = cat.selectFirst("h2");
            JSONArray entries = new JSONArray();
            for (Element row : cat.select("> div.row")) {
                Element h3 = row.selectFirst("h3");
                StringBuilder text = new StringBuilder();
                for (Element p : row.select("p")) {
                    String t = p.text().trim();
                    if (t.isEmpty()) continue;
                    if (text.length() > 0) text.append(' ');
                    text.append(t);
                }
                entries.put(new JSONObject()
                        .put("title", h3 == null ? "" : h3.text().replaceAll("\\s+", " ").trim())
                        .put("text", text.toString()));
            }
            cats.put(new JSONObject().put("name", h2 == null ? "" : h2.text().trim()).put("entries", entries));
        }
        if (cats.length() == 0) throw new JSONException("No categories in the road report");
        return new JSONObject().put("categories", cats);
    }

    /** The real update time and the first paragraphs of the general report. */
    public static JSONObject page(String html) throws JSONException {
        Document d = Jsoup.parse(html);
        Element time = d.selectFirst("time[datetime]");
        long updated = time == null ? 0 : Iso.parse(time.attr("datetime"), TimeZone.getTimeZone("Europe/Sarajevo"));
        Set<String> paras = new LinkedHashSet<String>();
        for (Element p : d.select("div.text-editor p")) {
            String t = p.text().trim();
            if (t.length() > 20) paras.add(t);
            if (paras.size() >= 3) break;
        }
        JSONArray summary = new JSONArray();
        for (String s : paras) summary.put(s);
        return new JSONObject().put("updated", updated).put("summary", summary);
    }
}
