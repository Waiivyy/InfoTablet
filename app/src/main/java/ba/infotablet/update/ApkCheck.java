package ba.infotablet.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import ba.infotablet.BuildConfig;

/**
 * Checks a downloaded APK before it is offered: same package, a higher version code, and signed
 * with the same key as the installed app. Android checks the signature again when installing;
 * checking first means a wrong file is never offered to the user.
 */
final class ApkCheck {

    private ApkCheck() {}

    /** Returns the APK's version code, or throws with a reason the user can read. */
    @SuppressWarnings("deprecation") // GET_SIGNATURES: the only way below Android 9, and enough for one fixed key
    static int verify(Context c, File apk) throws IOException {
        PackageManager pm = c.getPackageManager();
        PackageInfo theirs = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
        if (theirs == null) throw new IOException("Preuzeta datoteka nije ispravna aplikacija.");
        if (!c.getPackageName().equals(theirs.packageName)) throw new IOException("Datoteka je za drugu aplikaciju.");
        if (theirs.versionCode <= BuildConfig.VERSION_CODE) throw new IOException("Datoteka nije novija od instalirane verzije.");
        Set<String> want;
        try {
            want = fingerprints(pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES).signatures);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException(e.toString());
        }
        Set<String> got;
        if (theirs.signatures != null && theirs.signatures.length > 0) {
            got = fingerprints(theirs.signatures);
        } else {
            // Some Android versions do not fill in the signatures of an archive; read them from the JAR signature.
            Log.i("InfoTablet", "update: archive signatures not reported, reading the JAR signature");
            got = jarSigners(apk);
        }
        if (want.isEmpty() || !want.equals(got)) throw new IOException("Datoteka nije potpisana istim ključem kao ova aplikacija.");
        return theirs.versionCode;
    }

    private static Set<String> fingerprints(Signature[] sigs) throws IOException {
        Set<String> out = new HashSet<String>();
        if (sigs != null) for (Signature s : sigs) out.add(sha256(s.toByteArray()));
        return out;
    }

    /** Signer certificates of every entry; all entries must be signed by the same set. */
    private static Set<String> jarSigners(File apk) throws IOException {
        JarFile jar = new JarFile(apk, true);
        try {
            Set<String> signers = null;
            byte[] buf = new byte[8192];
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
                JarEntry entry = e.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;
                InputStream in = jar.getInputStream(entry);
                try {
                    while (in.read(buf) != -1) {
                        // Reading the entry to the end makes JarFile check its digest.
                    }
                } finally {
                    in.close();
                }
                Certificate[] certs = entry.getCertificates();
                if (certs == null || certs.length == 0) throw new IOException("Datoteka nije potpisana.");
                Set<String> s = new HashSet<String>();
                for (Certificate cert : certs) s.add(sha256(cert.getEncoded()));
                if (signers == null) signers = s;
                else if (!signers.equals(s)) throw new IOException("Datoteka ima različite potpise.");
            }
            return signers == null ? new HashSet<String>() : signers;
        } catch (CertificateEncodingException e) {
            throw new IOException(e.toString());
        } finally {
            jar.close();
        }
    }

    static String sha256(byte[] data) throws IOException {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e.toString());
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return s.toString();
    }
}
