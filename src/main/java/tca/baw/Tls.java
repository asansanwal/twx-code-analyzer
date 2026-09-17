package tca.baw;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.*;
import java.util.Base64;
import javax.net.ssl.*;

/**
 * TLS trust for repository connections (Process Center, Business Automation Studio). By default the JVM trust store decides
 * (import a self-signed certificate with keytool or point -Djavax.net.ssl.trustStore at a store that holds it). A connection
 * can instead carry its server certificate (PEM): then exactly that certificate is trusted, whatever the host name, and
 * nothing else. There is no "trust any certificate" mode.
 */
public final class Tls {
    private Tls() {}
    /** Applies a pinned certificate (PEM text, empty = JVM defaults) to an HTTPS connection. */
    public static void apply(HttpURLConnection c, String pemCertificate) throws IOException {
        if (pemCertificate == null || pemCertificate.trim().isEmpty() || !(c instanceof HttpsURLConnection)) return;
        final X509Certificate pinned = parse(pemCertificate); HttpsURLConnection h = (HttpsURLConnection) c;
        h.setSSLSocketFactory(context(pinned).getSocketFactory());
        // the certificate is trusted for the host it was saved for: the peer must present exactly that certificate
        h.setHostnameVerifier(new HostnameVerifier() { public boolean verify(String host, SSLSession s) { try { Certificate[] chain = s.getPeerCertificates(); return chain.length > 0 && chain[0].equals(pinned); } catch (SSLPeerUnverifiedException e) { return false; } } });
    }
    /** An SSL context whose only trusted certificate is the given one (standard trust manager over a one-entry key store). */
    static SSLContext context(X509Certificate cert) throws IOException {
        try {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType()); ks.load(null, null); ks.setCertificateEntry("pinned", cert);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); tmf.init(ks);
            SSLContext ctx; try { ctx = SSLContext.getInstance("TLSv1.3"); } catch (java.security.NoSuchAlgorithmException e) { ctx = SSLContext.getInstance("TLSv1.2"); }   // 1.3 + 1.2 on current runtimes, 1.2 on old ones; never SSL / TLS 1.0 / 1.1
            ctx.init(null, tmf.getTrustManagers(), null); return ctx;
        } catch (java.security.GeneralSecurityException e) { throw new IOException("cannot create the SSL context for the pinned certificate: " + e.getMessage()); }
    }
    /** The X.509 certificate of a PEM text ({@code -----BEGIN CERTIFICATE-----} block, or bare base64). */
    public static X509Certificate parse(String pem) throws IOException {
        try {
            String b64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", "");
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
        } catch (Exception e) { throw new IOException("the server certificate is not a valid PEM certificate: " + e.getMessage()); }
    }
    /** A short description for the UI: subject and expiry, or the parse error. */
    public static String describe(String pem) {
        if (pem == null || pem.trim().isEmpty()) return "";
        try { X509Certificate c = parse(pem); return c.getSubjectX500Principal().getName() + ", expires " + new java.text.SimpleDateFormat("yyyy-MM-dd").format(c.getNotAfter()); } catch (IOException e) { return e.getMessage(); }
    }
    /** Validates a PEM text for storage: returns the normalised text, throws IllegalArgumentException when it is not a certificate. */
    public static String normalize(String pem) {
        if (pem == null || pem.trim().isEmpty()) return "";
        try { X509Certificate c = parse(pem); return "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(c.getEncoded()) + "\n-----END CERTIFICATE-----\n"; }
        catch (Exception e) { throw new IllegalArgumentException(e.getMessage()); }
    }
}
