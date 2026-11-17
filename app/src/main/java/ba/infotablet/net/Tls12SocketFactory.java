package ba.infotablet.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Fallback used only when Conscrypt cannot be loaded: Android 4.2 supports TLS 1.1 and 1.2
 * but leaves them disabled by default, so enable every supported modern protocol on each socket.
 */
final class Tls12SocketFactory extends SSLSocketFactory {

    private static final List<String> WANTED = Arrays.asList("TLSv1.3", "TLSv1.2", "TLSv1.1");

    private final SSLSocketFactory delegate;

    Tls12SocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }

    @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

    @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return patch(delegate.createSocket(s, host, port, autoClose));
    }

    @Override public Socket createSocket(String host, int port) throws IOException {
        return patch(delegate.createSocket(host, port));
    }

    @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return patch(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        return patch(delegate.createSocket(host, port));
    }

    @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return patch(delegate.createSocket(address, port, localAddress, localPort));
    }

    private static Socket patch(Socket s) {
        if (s instanceof SSLSocket) {
            SSLSocket ssl = (SSLSocket) s;
            List<String> enabled = new ArrayList<String>();
            for (String p : ssl.getSupportedProtocols()) {
                if (WANTED.contains(p)) enabled.add(p);
            }
            if (!enabled.isEmpty()) ssl.setEnabledProtocols(enabled.toArray(new String[0]));
        }
        return s;
    }
}
