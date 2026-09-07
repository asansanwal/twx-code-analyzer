package tca.baw;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.net.ssl.*;
import tca.util.Json;

/**
 * Exports a snapshot from a Process Center into a file, the way the Process Center console does it (no REST export exists on 8.6):
 * form login on /ProcessCenter, then ImportExportServlet exportStep=prepare (returns a server side file token) and exportStep=retrieve (the zip).
 * Self-signed certificates: the default trust store is tried first; on an SSL handshake failure the export is retried trusting any certificate
 * (the Process Center is normally the same server the application runs on).
 */
public final class ProcessCenterExport {
    private ProcessCenterExport() {}
    static final String SERVLET = "/ProcessCenter/repository/com.lombardisoftware.repository.Repository/ImportExportServlet";

    public static Map<String, Object> export(String baseUrl, String user, String password, String snapshotId, File target) throws IOException {
        if (baseUrl == null || baseUrl.trim().isEmpty()) throw new IllegalArgumentException("Process Center URL is empty (environment variable processCenterURL)");
        if (snapshotId == null || !snapshotId.matches("2064\\.[0-9a-f\\-]{36}")) throw new IllegalArgumentException("invalid snapshot id '" + snapshotId + "'");
        if (user == null || user.isEmpty()) throw new IllegalArgumentException("user name is required");
        String base = baseUrl.trim(); while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        try { return run(base, user, password, snapshotId, target, false); }
        catch (SSLException e) { return run(base, user, password, snapshotId, target, true); }
    }

    static Map<String, Object> run(String base, String user, String password, String snapshotId, File target, boolean trustAll) throws IOException {
        Session s = new Session(trustAll);
        s.request("GET", base + "/ProcessCenter/login.jsp", null).disconnect();
        HttpURLConnection login = s.request("POST", base + "/ProcessCenter/j_security_check", "j_username=" + enc(user) + "&j_password=" + enc(password == null ? "" : password));
        int code = login.getResponseCode(); String location = login.getHeaderField("Location"); login.disconnect();
        if (code != 302 || location == null || location.contains("login")) throw new IOException("Process Center login failed for user " + user + " (HTTP " + code + ")");
        HttpURLConnection prep = s.request("GET", base + SERVLET + "?snapshotId=" + snapshotId + "&exportStep=prepare", null);
        String token = read(prep).trim(); prep.disconnect();
        if (!token.matches("[A-Za-z0-9_]+\\|[A-Za-z0-9_.\\-]+")) throw new IOException("unexpected export prepare response: " + (token.length() > 200 ? token.substring(0, 200) : token));
        HttpURLConnection get = s.request("GET", base + SERVLET + "?snapshotId=" + snapshotId + "&exportStep=retrieve&exportFilePath=" + enc(token), null);
        String type = String.valueOf(get.getContentType());
        if (get.getResponseCode() != 200 || !type.contains("zip")) throw new IOException("export retrieve failed: HTTP " + get.getResponseCode() + " " + type);
        File tmp = new File(target.getParentFile(), target.getName() + ".part");
        try (InputStream in = get.getInputStream(); OutputStream out = new FileOutputStream(tmp)) { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n); }
        get.disconnect(); Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return Json.obj("fileName", target.getName(), "size", target.length(), "trustAll", trustAll);
    }

    /** Minimal cookie-keeping HTTP session (JSESSIONID + LTPA token). */
    static final class Session {
        final Map<String, String> cookies = new LinkedHashMap<>(); final boolean trustAll;
        Session(boolean trustAll) { this.trustAll = trustAll; }
        HttpURLConnection request(String method, String url, String form) throws IOException {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            if (trustAll && c instanceof HttpsURLConnection) { HttpsURLConnection h = (HttpsURLConnection) c; h.setSSLSocketFactory(trustAllFactory()); h.setHostnameVerifier(new HostnameVerifier() { public boolean verify(String host, SSLSession s) { return true; } }); }
            c.setRequestMethod(method); c.setInstanceFollowRedirects(false); c.setConnectTimeout(30000); c.setReadTimeout(600000); c.setUseCaches(false);
            if (!cookies.isEmpty()) { StringBuilder sb = new StringBuilder(); for (Map.Entry<String, String> e : cookies.entrySet()) { if (sb.length() > 0) sb.append("; "); sb.append(e.getKey()).append('=').append(e.getValue()); } c.setRequestProperty("Cookie", sb.toString()); }
            if (form != null) { c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); byte[] b = form.getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(b.length); try (OutputStream o = c.getOutputStream()) { o.write(b); } }
            c.getResponseCode();
            List<String> set = c.getHeaderFields().get("Set-Cookie");
            if (set != null) for (String sc : set) { int eq = sc.indexOf('='), sem = sc.indexOf(';'); if (eq > 0) cookies.put(sc.substring(0, eq).trim(), (sem > eq ? sc.substring(eq + 1, sem) : sc.substring(eq + 1)).trim()); }
            return c;
        }
    }
    static String read(HttpURLConnection c) throws IOException { InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream(); if (in == null) return ""; ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); in.close(); return new String(bo.toByteArray(), StandardCharsets.UTF_8); }
    static String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { return s; } }
    static SSLSocketFactory trustAllFactory() throws IOException {
        try { SSLContext ctx = SSLContext.getInstance("TLS"); ctx.init(null, new TrustManager[] { new X509TrustManager() { public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {} public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {} public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; } } }, new java.security.SecureRandom()); return ctx.getSocketFactory(); }
        catch (Exception e) { throw new IOException("cannot create SSL context: " + e); }
    }
}
