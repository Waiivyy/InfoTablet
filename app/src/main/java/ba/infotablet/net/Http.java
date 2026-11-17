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
            return read(body, cap);
        } catch (IOException e) {
            if ("Response too large".equals(e.getMessage())) throw new IOException("Response too large " + host(url));
            throw e;
        } finally {
            r.close();
        }
    }

    /** The body as text, or an IOException("Response too large") past {@code cap} bytes. */
    public static String read(ResponseBody body, long cap) throws IOException {
        BufferedSource src = body.source();
        Buffer buf = new Buffer();
        while (buf.size() <= cap) {
            if (src.read(buf, 8192) == -1) return buf.readString(charset(body));
        }
        throw new IOException("Response too large");
    }

    /**
     * GET a page but stop reading once {@code marker} has been seen plus {@code extraBytes}, or at
     * {@code cap}. News articles are 160 to 460 KB, while the text the app needs sits near the
     * start of the body; this keeps memory low on the 1 GB tablet. Pages are decoded as UTF-8.
     */
    public static String getPartial(String url, String marker, int extraBytes, int cap) throws IOException {
        Response r = Net.client().newCall(new Request.Builder().url(url).build()).execute();
        try {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code() + " " + host(url));
            ResponseBody body = r.body();
            if (body == null) throw new IOException("Empty body " + host(url));
            byte[] needle = marker.getBytes(UTF8);
            java.io.InputStream in = body.byteStream();
            byte[] buf = new byte[Math.min(cap, 512 * 1024)];
            int len = 0, stopAt = cap, searchFrom = 0;
            while (len < stopAt) {
                int n = in.read(buf, len, Math.min(8192, stopAt - len));
                if (n < 0) break;
                len += n;
                if (stopAt == cap) {
                    int at = indexOf(buf, len, needle, Math.max(0, searchFrom - needle.length));
                    searchFrom = len;
                    if (at >= 0) stopAt = Math.min(cap, at + needle.length + extraBytes);
                }
                if (len == buf.length && len < stopAt) buf = java.util.Arrays.copyOf(buf, Math.min(cap, buf.length * 2));
            }
            return new String(buf, 0, len, UTF8);
        } finally {
            r.close();
        }
    }

    private static int indexOf(byte[] hay, int len, byte[] needle, int from) {
        outer:
        for (int i = from; i <= len - needle.length; i++) {
            for (int k = 0; k < needle.length; k++) if (hay[i + k] != needle[k]) continue outer;
            return i;
        }
        return -1;
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
