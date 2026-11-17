package ba.infotablet.debug;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.util.Xml;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

import org.conscrypt.Conscrypt;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.security.Provider;
import java.security.Security;
import java.util.Locale;

import ba.infotablet.net.Net;
import ba.infotablet.net.TrustStore;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Debug-only diagnostics: library compatibility on this device and HTTPS to every data source
 * with three TLS set-ups (stock platform, Conscrypt with system roots, Conscrypt with system and
 * bundled roots = production). Results are shown on screen and logged under tag "SelfTest".
 */
public final class SelfTestActivity extends Activity {

    private static final String TAG = "SelfTest";

    /** One small, polite URL per host the app uses (robots.txt where the site has no tiny endpoint). */
    private static final String[] URLS = {
            "https://api.open-meteo.com/v1/forecast?latitude=43.8563&longitude=18.4131&current=temperature_2m",
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=43.8563&longitude=18.4131&current=european_aqi",
            "https://zrak.fhmzbih.gov.ba/rest/index/data-station/3/0",
            "https://www.fhmzbih.gov.ba/latinica/ZRAK/AQI-satne.php",
            "https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-rss-bosnia-herzegovina",
            "https://vlada.ks.gov.ba/robots.txt",
            "https://uhvatipopust.ba/robots.txt",
            "https://www.klix.ba/robots.txt",
            "https://n1info.ba/robots.txt",
            "https://radiosarajevo.ba/robots.txt",
            "https://www.nfsbih.ba/robots.txt",
            "https://www.thesportsdb.com/api/v1/json/123/lookupleague.php?id=4624",
            "https://www.cbbh.ba/CurrencyExchange/GetJson?date=2026-11-17",
            "https://www.viksa.ba/robots.txt",
            "https://bihamk.ba/robots.txt",
            "https://api.github.com/rate_limit",
            "https://github.com/robots.txt",
            "https://objects.githubusercontent.com/",
            "https://release-assets.githubusercontent.com/",
    };

    private TextView out;
    private final StringBuilder log = new StringBuilder();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScrollView sv = new ScrollView(this);
        out = new TextView(this);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(11);
        out.setPadding(12, 12, 12, 12);
        sv.addView(out);
        setContentView(sv);
        new Thread(this::run, "selftest").start();
    }

    private void run() {
        line("INFO|device=" + Build.MODEL + "|api=" + Build.VERSION.SDK_INT + "|abi=" + Build.CPU_ABI);
        try {
            line("INFO|conscrypt.available=" + Conscrypt.isAvailable() + "|active=" + Net.conscryptActive()
                    + "|version=" + (Conscrypt.isAvailable() ? Conscrypt.version().major() + "." + Conscrypt.version().minor() + "." + Conscrypt.version().patch() : "-"));
        } catch (Throwable t) {
            line("INFO|conscrypt.error=" + t);
        }
        Provider[] ps = Security.getProviders();
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < Math.min(4, ps.length); i++) order.append(i == 0 ? "" : ",").append(ps[i].getName());
        line("INFO|providers=" + order);

        testJsoup();
        testXmlJson();

        String[] modes = {"STOCK", "CONSCRYPT_SYS", "APP"};
        int[] ok = new int[modes.length];
        for (int m = 0; m < modes.length; m++) {
            OkHttpClient client;
            try {
                if (m == 0) client = Net.build(this, TrustStore.Mode.SYSTEM_ONLY, false);
                else if (m == 1) client = Net.build(this, TrustStore.Mode.SYSTEM_ONLY, true);
                else client = Net.client();
            } catch (Throwable t) {
                line("RESULT|" + modes[m] + "|*|FAIL|client|" + t);
                continue;
            }
            for (String url : URLS) {
                if (request(client, modes[m], url)) ok[m]++;
            }
        }
        for (int m = 0; m < modes.length; m++) line("SUMMARY|" + modes[m] + "|" + ok[m] + "/" + URLS.length);
        Runtime rt = Runtime.getRuntime();
        line("INFO|heapUsedKB=" + (rt.totalMemory() - rt.freeMemory()) / 1024 + "|heapMaxKB=" + rt.maxMemory() / 1024);
        line("DONE");
    }

    private boolean request(OkHttpClient client, String mode, String url) {
        String host = url.replaceFirst("https://", "").replaceFirst("/.*", "");
        long t0 = System.currentTimeMillis();
        Response r = null;
        try {
            r = client.newCall(new Request.Builder().url(url).build()).execute();
            long ms = System.currentTimeMillis() - t0;
            String tls = r.handshake() == null ? "-" : r.handshake().tlsVersion().javaName();
            String cipher = r.handshake() == null ? "-" : r.handshake().cipherSuite().javaName();
            // Any HTTP status proves the TLS handshake and certificate validation succeeded.
            line("RESULT|" + mode + "|" + host + "|OK|" + r.code() + "|" + tls + "|" + cipher + "|" + ms + "ms");
            return true;
        } catch (Throwable t) {
            long ms = System.currentTimeMillis() - t0;
            String msg = String.valueOf(t.getMessage());
            if (msg.length() > 120) msg = msg.substring(0, 120);
            line("RESULT|" + mode + "|" + host + "|FAIL|" + t.getClass().getSimpleName() + "|" + msg + "|" + ms + "ms");
            return false;
        } finally {
            if (r != null) r.close();
        }
    }

    private void testJsoup() {
        try {
            String html = "<html><head><meta property='og:description' content='Prva rečenica. Druga.'></head><body>"
                    + "<div id='excerpt'><span class='lead'>Općina &quot;Stari Grad&quot; čćđšž.</span></div>"
                    + "<div id='text'><p>Prvi pasus.</p><p>Drugi pasus.</p><blockquote><p>ugniježđen</p></blockquote></div>"
                    + "<div data-testid='article-content-wrapper-0'><div class='rich-text-block'><p>N1 tekst.</p></div></div>"
                    + "<table class='table-striped'><tr><td class='IKZ'><a href='amsBjelave.php'>Bjelave</a></td><td>22</td></tr></table>"
                    + "</body></html>";
            Document d = Jsoup.parse(html);
            String lead = d.select("div#excerpt span.lead").text();
            int paragraphs = d.select("div#text > p").size();
            String n1 = d.select("div[data-testid^=article-content-wrapper] > div.rich-text-block > p").text();
            String og = d.select("meta[property=og:description]").attr("content");
            int has = d.select("tr:has(td.IKZ):has(a[href^=ams])").size();
            String whole = d.select("div#text").first().wholeText();
            String unescaped = Parser.unescapeEntities("&quot;Sumraka&quot; &amp; č", false);
            Document frag = Jsoup.parseBodyFragment("<div class='category'><h2>Granični prijelazi</h2>"
                    + "<div class='row'><h3>GP  Brčko</h3><p>Zadržavanja nisu duža od 30 minuta.</p></div></div>");
            String h3 = frag.select("div.category > div.row h3").text();
            boolean pass = lead.equals("Općina \"Stari Grad\" čćđšž.") && paragraphs == 2 && n1.equals("N1 tekst.")
                    && og.startsWith("Prva") && has == 1 && whole.contains("Drugi pasus") && unescaped.equals("\"Sumraka\" & č")
                    && h3.equals("GP Brčko");
            line("LIB|jsoup|" + (pass ? "OK" : "FAIL") + "|lead=" + lead + "|p=" + paragraphs + "|has=" + has + "|h3=" + h3);
        } catch (Throwable t) {
            line("LIB|jsoup|FAIL|" + t);
        }
    }

    private void testXmlJson() {
        try {
            XmlPullParser x = Xml.newPullParser();
            x.setInput(new StringReader("<rss><channel><item><title>Naslov č</title><pubDate>Mon, 16 Nov 2026 23:25:00 +0100</pubDate></item></channel></rss>"));
            String title = null;
            for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
                if (e == XmlPullParser.START_TAG && "title".equals(x.getName())) title = x.nextText();
            }
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
            long when = f.parse("Mon, 16 Nov 2026 23:25:00 +0100").getTime();
            JSONObject o = new JSONObject("{\"Middle\":\"1.775767\",\"Units\":\"1\"}");
            JsonReader jr = new JsonReader(new StringReader("{\"warnings\":[{\"a\":1}]}"));
            jr.beginObject();
            String name = jr.nextName();
            jr.skipValue();
            jr.endObject();
            boolean pass = "Naslov č".equals(title) && when == 1794867900000L
                    && Double.parseDouble(o.getString("Middle")) > 1.7 && "warnings".equals(name);
            line("LIB|xml+json|" + (pass ? "OK" : "FAIL") + "|title=" + title + "|when=" + when);
        } catch (Throwable t) {
            line("LIB|xml+json|FAIL|" + t);
        }
    }

    private void line(final String s) {
        Log.i(TAG, s);
        runOnUiThread(() -> {
            log.append(s).append('\n');
            out.setText(log);
        });
    }
}
