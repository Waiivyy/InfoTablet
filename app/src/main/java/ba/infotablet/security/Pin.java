package ba.infotablet.security;

import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PIN hashing (PBKDF2 with a random salt) and a lock-out after repeated wrong attempts. The PIN
 * itself is never stored. A 4 to 8 digit PIN is short, so the lock-out does the real work.
 * SecureRandom comes from Conscrypt (first provider, see Net.init), which avoids the seeding flaw
 * of the stock Android 4.2 generator.
 */
public final class Pin {

    public static final int MIN_LENGTH = 4, MAX_LENGTH = 8;
    private static final int ITERATIONS = 8000;
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MS = 5 * 60_000L;

    private static int failures;
    private static long lockedUntil;

    private Pin() {}

    public static boolean valid(String pin) {
        if (pin == null || pin.length() < MIN_LENGTH || pin.length() > MAX_LENGTH) return false;
        for (int i = 0; i < pin.length(); i++) if (pin.charAt(i) < '0' || pin.charAt(i) > '9') return false;
        return true;
    }

    /** "salt:hash" in hex, for storing. */
    @android.annotation.SuppressLint("TrulyRandom") // Conscrypt's generator, verified on the device
    public static String hash(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return hex(salt) + ":" + hex(derive(pin, salt));
    }

    /** Checks a PIN against a stored "salt:hash" in constant time. */
    public static boolean matches(String pin, String stored) {
        if (pin == null || stored == null) return false;
        int colon = stored.indexOf(':');
        if (colon <= 0) return false;
        byte[] expected = unhex(stored.substring(colon + 1));
        byte[] actual = derive(pin, unhex(stored.substring(0, colon)));
        if (expected.length != actual.length) return false;
        int diff = 0;
        for (int i = 0; i < actual.length; i++) diff |= expected[i] ^ actual[i];
        return diff == 0;
    }

    /** Milliseconds until another attempt is allowed (0 when not locked). Shared by the screen and the Wi-Fi page. */
    public static synchronized long lockedFor(long now) {
        return Math.max(0, lockedUntil - now);
    }

    /** Checks an attempt and counts failures; returns false while locked. */
    public static synchronized boolean attempt(String pin, String stored, long now) {
        if (lockedFor(now) > 0) return false;
        if (matches(pin, stored)) {
            failures = 0;
            return true;
        }
        if (++failures >= MAX_FAILURES) {
            failures = 0;
            lockedUntil = now + LOCK_MS;
        }
        return false;
    }

    private static byte[] derive(String pin, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return s.toString();
    }

    private static byte[] unhex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
