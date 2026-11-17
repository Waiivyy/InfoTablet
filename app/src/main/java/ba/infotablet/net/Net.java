package ba.infotablet.net;

import android.content.Context;
import android.util.Log;

import org.conscrypt.Conscrypt;

import java.security.Provider;
import java.security.Security;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import ba.infotablet.BuildConfig;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;

/**
 * The single HTTP client of the app: OkHttp 3.12 over Conscrypt (TLS 1.2/1.3 on Android 4.2),
 * a descriptive User-Agent and conservative timeouts. Every request in the app goes through here.
 */
public final class Net {

    public static final String TAG = "InfoTablet";

    private static OkHttpClient client;
    private static Provider conscrypt;
    private static String userAgent;

    private Net() {}

    public static synchronized void init(Context ctx) {
        if (client != null) return;
        conscrypt = installConscrypt();
        userAgent = "InfoTablet/" + BuildConfig.VERSION_NAME + " (home information display)";
        try {
            client = build(ctx.getApplicationContext(), TrustStore.Mode.SYSTEM_AND_BUNDLED, true);
        } catch (Exception e) {
            Log.e(TAG, "Could not build the HTTPS client", e);
            client = new OkHttpClient.Builder().build();
        }
    }

    public static OkHttpClient client() {
        return client;
    }

    public static String userAgent() {
        return userAgent;
    }

    public static boolean conscryptActive() {
        return conscrypt != null;
    }

    /**
     * Builds a client with the given trust mode. {@code useConscrypt=false} gives the stock
     * platform TLS stack (with TLS 1.2 enabled), used only by the diagnostics.
     */
    public static OkHttpClient build(Context ctx, TrustStore.Mode mode, boolean useConscrypt) throws Exception {
        X509TrustManager tm = TrustStore.trustManager(ctx, mode);
        SSLContext ssl = (useConscrypt && conscrypt != null)
                ? SSLContext.getInstance("TLS", conscrypt)
                : SSLContext.getInstance("TLS", "AndroidOpenSSL");
        ssl.init(null, new TrustManager[] {tm}, null);
        SSLSocketFactory factory = ssl.getSocketFactory();
        if (!(useConscrypt && conscrypt != null)) factory = new Tls12SocketFactory(factory);

        final String ua = userAgent;
        return new OkHttpClient.Builder()
                .sslSocketFactory(factory, tm)
                // HTTP/1.1 only: simpler and lighter on this device; the sources gain nothing from HTTP/2.
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addNetworkInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder().header("User-Agent", ua).build()))
                .build();
    }

    private static Provider installConscrypt() {
        try {
            if (!Conscrypt.isAvailable()) {
                Log.w(TAG, "Conscrypt native library is not available; using platform TLS");
                return null;
            }
            Provider p = Conscrypt.newProvider();
            Security.insertProviderAt(p, 1);
            return p;
        } catch (Throwable t) {
            Log.e(TAG, "Conscrypt could not be installed; using platform TLS", t);
            return null;
        }
    }
}
