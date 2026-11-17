package ba.infotablet.net;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Trust anchors for HTTPS. Android 4.2 ships a 2013 root store that lacks today's common roots
 * (for example ISRG Root X1 used by Let's Encrypt), so the app trusts the system roots plus a
 * small bundle of current public roots from assets/certs/roots.pem.
 */
public final class TrustStore {

    public enum Mode { SYSTEM_ONLY, SYSTEM_AND_BUNDLED }

    private static final String BUNDLE = "certs/roots.pem";

    private TrustStore() {}

    public static X509TrustManager trustManager(Context ctx, Mode mode) throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);

        KeyStore system = KeyStore.getInstance("AndroidCAStore");
        system.load(null, null);
        for (Enumeration<String> e = system.aliases(); e.hasMoreElements(); ) {
            String alias = e.nextElement();
            Certificate c = system.getCertificate(alias);
            if (c != null) ks.setCertificateEntry("sys:" + alias, c);
        }

        if (mode == Mode.SYSTEM_AND_BUNDLED) {
            List<Certificate> bundled = readBundle(ctx);
            for (int i = 0; i < bundled.size(); i++) ks.setCertificateEntry("app:" + i, bundled.get(i));
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) return (X509TrustManager) tm;
        }
        throw new KeyStoreException("No X509TrustManager available");
    }

    /** Parses PEM blocks one by one; comment lines between blocks are ignored. */
    static List<Certificate> readBundle(Context ctx) throws IOException, GeneralSecurityException {
        String pem = readAll(ctx.getAssets().open(BUNDLE));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<Certificate> out = new ArrayList<Certificate>();
        String begin = "-----BEGIN CERTIFICATE-----";
        String end = "-----END CERTIFICATE-----";
        int from = 0;
        while (true) {
            int b = pem.indexOf(begin, from);
            if (b < 0) break;
            int e = pem.indexOf(end, b);
            if (e < 0) break;
            String body = pem.substring(b + begin.length(), e).replaceAll("\\s", "");
            byte[] der = Base64.decode(body, Base64.DEFAULT);
            out.add(cf.generateCertificate(new ByteArrayInputStream(der)));
            from = e + end.length();
        }
        return out;
    }

    private static String readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("US-ASCII");
        } finally {
            in.close();
        }
    }
}
