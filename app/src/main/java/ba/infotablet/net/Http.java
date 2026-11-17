package ba.infotablet.net;

import java.io.IOException;
import java.nio.charset.Charset;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

/** Small GET helpers on top of the shared client. */
public final class Http {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long DEFAULT_CAP = 2L * 1024 * 1024;

    private Http() {}

    public static String get(String url) throws IOException {
        return get(url, DEFAULT_CAP);
    }

    /** GET a URL and return the body as text; fails on non-2xx status or a body larger than {@code cap}. */
    public static String get(String url, long cap) throws IOException {
        Response r = Net.client().newCall(new Request.Builder().url(url).build()).execute();
        try {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code() + " " + host(url));
            ResponseBody body = r.body();
            if (body == null) throw new IOException("Empty body " + host(url));
            BufferedSource src = body.source();
            Buffer buf = new Buffer();
            while (buf.size() <= cap) {
                if (src.read(buf, 8192) == -1) return buf.readString(charset(body));
            }
            throw new IOException("Response too large " + host(url));
        } finally {
            r.close();
        }
    }

    private static Charset charset(ResponseBody body) {
        MediaType type = body.contentType();
        Charset c = type == null ? null : type.charset(null);
        return c == null ? UTF8 : c;
    }

    private static String host(String url) {
        return url.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
    }
}
