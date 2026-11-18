package ba.infotablet.web;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import ba.infotablet.Settings;
import ba.infotablet.reminders.Agenda;
import ba.infotablet.reminders.Reminder;
import ba.infotablet.reminders.ReminderStore;
import ba.infotablet.security.Pin;
import ba.infotablet.util.Clock;
import fi.iki.elonen.NanoHTTPD;

/**
 * The Wi-Fi page: add and change reminders from a phone on the home network. Runs only when a PIN
 * is set and the page is switched on. Requests from outside private (home network) addresses are
 * refused. Sessions use an HttpOnly, SameSite=Strict cookie, and every form carries a per-session
 * token. Plain HTTP: the PIN crosses the home Wi-Fi unencrypted, like a router's own admin page.
 */
public final class WebServer extends NanoHTTPD {

    public static final int PORT = 8080;
    private static final String TAG = "InfoTablet";
    private static final long SESSION_MS = 12 * 3600_000L;
    private static final int MAX_BODY = 16 * 1024;
    private static final int MAX_SESSIONS = 20;

    private static WebServer running;

    private final Context ctx;
    private final SecureRandom random = new SecureRandom();
    private static final class Session {
        final long expires;
        final String csrf;

        Session(long expires, String csrf) {
            this.expires = expires;
            this.csrf = csrf;
        }
    }

    private final Map<String, Session> sessions = new HashMap<String, Session>();

    private WebServer(Context ctx) {
        super(PORT);
        this.ctx = ctx.getApplicationContext();
    }

    /** Starts or stops the server to match the settings. */
    public static synchronized void update(Context ctx) {
        Settings s = Settings.of(ctx);
        boolean want = s.hasPin() && s.webEnabled();
        if (want && running == null) {
            // Small form bodies only; keep any temporary files in the app's own cache.
            System.setProperty("java.io.tmpdir", ctx.getCacheDir().getAbsolutePath());
            WebServer w = new WebServer(ctx);
            try {
                w.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
                running = w;
                // Must be Conscrypt (installed first in App): the stock 4.2 SecureRandom has a known seeding flaw.
                Log.i(TAG, "web: listening on port " + PORT + ", random from " + w.random.getProvider().getName());
            } catch (IOException e) {
                Log.w(TAG, "web: could not start: " + e);
            }
        } else if (!want && running != null) {
            running.stop();
            running = null;
            Log.i(TAG, "web: stopped");
        }
    }

    public static synchronized boolean isRunning() {
        return running != null;
    }

    /** "http://192.168.1.23:8080" for the current Wi-Fi address, or null without Wi-Fi. */
    @SuppressWarnings("deprecation")
    public static String address(Context ctx) {
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wm == null ? null : wm.getConnectionInfo();
        int ip = info == null ? 0 : info.getIpAddress();
        if (ip == 0) return null;
        return String.format(Locale.ROOT, "http://%d.%d.%d.%d:%d", ip & 0xFF, (ip >> 8) & 0xFF, (ip >> 16) & 0xFF, (ip >> 24) & 0xFF, PORT);
    }

    @Override
    public Response serve(IHTTPSession s) {
        try {
            if (!fromHomeNetwork(s.getRemoteIpAddress())) return text(Response.Status.FORBIDDEN, "Samo s kućne mreže.");
            String uri = s.getUri();
            if (uri.startsWith("/f/")) return font(uri);
            Map<String, String> form = new HashMap<String, String>();
            if (s.getMethod() == Method.POST) {
                String len = s.getHeaders().get("content-length");
                if (len == null || Long.parseLong(len.trim()) > MAX_BODY) return text(Response.Status.BAD_REQUEST, "Zahtjev je prevelik.");
                s.parseBody(new HashMap<String, String>());
                form.putAll(s.getParms());
                if (!sameOrigin(s.getHeaders().get("origin"), s.getHeaders().get("host"))) return text(Response.Status.FORBIDDEN, "Zahtjev odbijen.");
            } else {
                form.putAll(s.getParms());
            }
            long now = Clock.now();
            if (uri.equals("/login") && s.getMethod() == Method.POST) return login(form.get("pin"), now);

            String token = s.getCookies().read("s");
            Session session = session(token, now);
            if (session == null) return html(Pages.login(null));
            String csrf = session.csrf;
            if (s.getMethod() == Method.POST && !csrf.equals(form.get("csrf"))) {
                return html(Pages.message("Stranica je istekla", "Otvorite stranicu ponovo i pokušajte još jednom."));
            }
            return route(s.getMethod(), uri, form, token, csrf, now);
        } catch (Exception e) {
            Log.w(TAG, "web: " + e);
            return text(Response.Status.INTERNAL_ERROR, "Greška. Pokušajte ponovo.");
        }
    }

    private Response route(Method m, String uri, Map<String, String> f, String token, String csrf, long now) {
        if (m == Method.GET && uri.equals("/")) {
            return html(Pages.list(Agenda.all(ReminderStore.all(ctx), now), now, csrf, msg(f.get("m"))));
        }
        if (m == Method.GET && uri.equals("/new")) return html(Pages.form(Pages.fresh(now), true, null, csrf));
        if (m == Method.GET && uri.equals("/history")) {
            return html(Pages.history(ReminderStore.history(ctx, now - ba.infotablet.reminders.History.SHOW_MS), now));
        }
        if (m == Method.GET && uri.equals("/backup")) {
            try {
                Response r = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8",
                        ba.infotablet.data.Backup.create(ctx, now).toString(1));
                r.addHeader("Content-Disposition", "attachment; filename=\"" + ba.infotablet.data.Backup.fileName(now) + "\"");
                secure(r);
                return r;
            } catch (org.json.JSONException e) {
                return text(Response.Status.INTERNAL_ERROR, "Kopija nije napravljena.");
            }
        }
        if (m == Method.GET && uri.equals("/edit")) {
            Reminder r = find(f.get("id"));
            return r == null ? redirect("/") : html(Pages.form(r, false, null, csrf));
        }
        if (m == Method.GET && uri.equals("/delete")) {
            Reminder r = find(f.get("id"));
            return r == null ? redirect("/") : html(Pages.confirmDelete(r, csrf));
        }
        if (m == Method.POST && uri.equals("/save")) {
            ReminderForm form = ReminderForm.parse(f, now);
            Reminder existing = find(f.get("id"));
            boolean isNew = existing == null;
            if (form.error != null) {
                if (!isNew) form.reminder.id = existing.id;
                return html(Pages.form(form.reminder, isNew, form.error, csrf));
            }
            Reminder r = form.reminder;
            if (!isNew) {
                r.id = existing.id;
                r.createdAt = existing.createdAt;
            }
            ReminderStore.save(ctx, r, now);
            return redirect("/?m=" + (isNew ? "added" : "saved"));
        }
        if (m == Method.POST && uri.equals("/delete")) {
            Reminder r = find(f.get("id"));
            if (r != null) ReminderStore.delete(ctx, r.id);
            return redirect("/?m=deleted");
        }
        if (m == Method.POST && uri.equals("/done")) {
            Reminder r = find(f.get("id"));
            if (r != null) ReminderStore.done(ctx, r.id, now);
            return redirect("/");
        }
        if (m == Method.POST && uri.equals("/logout")) {
            synchronized (sessions) {
                sessions.remove(token);
            }
            Response res = redirect("/");
            res.addHeader("Set-Cookie", "s=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict");
            return res;
        }
        return text(Response.Status.NOT_FOUND, "Nema te stranice.");
    }

    private Response login(String pin, long now) {
        Settings st = Settings.of(ctx);
        long locked = Pin.lockedFor(now);
        if (locked > 0) {
            return html(Pages.login("Previše pogrešnih pokušaja. Pokušajte ponovo za " + Math.max(1, (locked + 59_999) / 60_000) + " min."));
        }
        if (!Pin.attempt(pin == null ? "" : pin.trim(), st.pinHash(), now)) return html(Pages.login("Pogrešan PIN."));
        String token = randomHex(16);
        synchronized (sessions) {
            prune(now);
            if (sessions.size() >= MAX_SESSIONS) sessions.clear();
            sessions.put(token, new Session(now + SESSION_MS, randomHex(16)));
        }
        Response res = redirect("/");
        res.addHeader("Set-Cookie", "s=" + token + "; Max-Age=" + SESSION_MS / 1000 + "; Path=/; HttpOnly; SameSite=Strict");
        return res;
    }

    private Session session(String token, long now) {
        if (token == null) return null;
        synchronized (sessions) {
            Session s = sessions.get(token);
            return s == null || s.expires < now ? null : s;
        }
    }

    private void prune(long now) {
        for (Iterator<Session> it = sessions.values().iterator(); it.hasNext(); ) {
            if (it.next().expires < now) it.remove();
        }
    }

    private Reminder find(String id) {
        if (id == null) return null;
        try {
            long n = Long.parseLong(id.trim());
            return n > 0 ? ReminderStore.get(ctx, n) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String msg(String code) {
        if ("added".equals(code)) return "Podsjetnik je dodan. Tablet će zvoniti u zadano vrijeme.";
        if ("saved".equals(code)) return "Promjene su sačuvane.";
        if ("deleted".equals(code)) return "Podsjetnik je obrisan.";
        return null;
    }

    /**
     * Browsers send Origin on form posts; a real one must be this page. A missing or "null" origin
     * is left to the form token, which is the actual protection.
     */
    static boolean sameOrigin(String origin, String host) {
        return origin == null || origin.equals("null") || (host != null && origin.equals("http://" + host));
    }

    /** Private IPv4 ranges, link-local and loopback; anything else is not the home network. */
    static boolean fromHomeNetwork(String ip) {
        if (ip == null) return false;
        try {
            InetAddress a = InetAddress.getByName(ip); // a literal address, no DNS lookup
            return a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private Response font(String uri) throws IOException {
        String file = uri.equals("/f/r.ttf") ? "Roboto-Regular.ttf" : uri.equals("/f/m.ttf") ? "Roboto-Medium.ttf"
                : uri.equals("/f/mm.ttf") ? "RobotoMono-Medium.ttf" : null;
        if (file == null) return text(Response.Status.NOT_FOUND, "Nema te stranice.");
        InputStream in = ctx.getAssets().open("fonts/" + file);
        Response r = newFixedLengthResponse(Response.Status.OK, "font/ttf", in, in.available());
        r.addHeader("Cache-Control", "max-age=604800");
        return r;
    }

    private static Response html(String body) {
        Response r = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body);
        secure(r);
        return r;
    }

    private static Response text(Response.Status status, String body) {
        Response r = newFixedLengthResponse(status, "text/plain; charset=utf-8", body);
        secure(r);
        return r;
    }

    private static Response redirect(String to) {
        Response r = newFixedLengthResponse(Response.Status.REDIRECT_SEE_OTHER, "text/plain; charset=utf-8", "");
        r.addHeader("Location", to);
        secure(r);
        return r;
    }

    private static void secure(Response r) {
        r.addHeader("Cache-Control", "no-store");
        r.addHeader("Content-Security-Policy", "default-src 'self'; style-src 'unsafe-inline'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'");
        r.addHeader("X-Frame-Options", "DENY");
        r.addHeader("X-Content-Type-Options", "nosniff");
        r.addHeader("Referrer-Policy", "same-origin");
    }

    private String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return s.toString();
    }
}
