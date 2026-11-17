package ba.infotablet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Loads files from src/test/resources/fixtures. */
public final class Fixtures {

    private Fixtures() {}

    public static String read(String name) {
        InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name);
        if (in == null) throw new IllegalArgumentException("Missing fixture " + name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
