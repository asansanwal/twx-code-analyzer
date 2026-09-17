package tca.enterprise;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import tca.util.Json;

/**
 * Business Automation Studio (Cloud Pak for Business Automation, Workflow Authoring) access. The repository API is the
 * same as the Process Center's ({@code /rest/bpm/wle/v1/processApps}, the console's ImportExportServlet) but lives behind
 * the platform gateway (Zen) under a context root (default {@code /bas}) and is authenticated with a bearer token:
 * a Zen token obtained from {@code /icp4d-api/v1/authorize} (user name + password or API key), a token pasted by the user,
 * or basic authentication where the gateway allows it. Certificates: the JVM trust store, or the server certificate given with the call (PEM, pinned).
 */
public final class Studio {
    private Studio() {}
    /** Authorization header value for a definition: Bearer token (given or obtained from Zen) or Basic. */
    @SuppressWarnings("unchecked")
    public static String authorization(String baseUrl, String auth, String user, String password, String apiKey, String token, String certificate) throws IOException {
        String base = trim(baseUrl); auth = auth == null ? "" : auth.toLowerCase();
        if (auth.equals("bearer") || (auth.isEmpty() && token != null && !token.isEmpty())) { if (token == null || token.trim().isEmpty()) throw new IOException("a bearer token is required"); return "Bearer " + token.trim(); }
        if (auth.equals("basic")) return "Basic " + Base64.getEncoder().encodeToString((user + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
        // zen: user name + password, or user name + API key
        Map<String, Object> body = apiKey != null && !apiKey.isEmpty() ? Json.obj("username", user, "api_key", apiKey) : Json.obj("username", user, "password", password == null ? "" : password);
        String[] endpoints = { base + "/icp4d-api/v1/authorize", base + "/v1/preauth/validateAuth" }; IOException last = null;
        for (String ep : endpoints) {
            try {
                String resp = ep.endsWith("validateAuth") ? request("GET", ep, null, "Basic " + Base64.getEncoder().encodeToString((user + ":" + (apiKey != null && !apiKey.isEmpty() ? apiKey : password)).getBytes(StandardCharsets.UTF_8)), null, certificate) : request("POST", ep, Json.write(body), null, "application/json", certificate);
                Object o = Json.parse(resp); if (o instanceof Map) { Map<String, Object> m = (Map<String, Object>) o; Object t = m.get("token") != null ? m.get("token") : m.get("accessToken"); if (t != null && !String.valueOf(t).isEmpty()) return "Bearer " + t; }
                last = new IOException("no token in the answer of " + ep);
            } catch (IOException e) { last = e; }
        }
        throw new IOException("Studio authentication failed: " + (last == null ? "" : last.getMessage()));
    }
    /** Process applications with their snapshots and tracks (same shape as the Process Center listing). */
    public static List<Map<String, Object>> apps(String baseUrl, String contextRoot, String authorization, String certificate) throws IOException {
        String body = request("GET", trim(baseUrl) + ctx(contextRoot) + "/rest/bpm/wle/v1/processApps", null, authorization, null, certificate);
        return ProcessCenterClient.parseApps(body);
    }
    /** Exports a snapshot through the repository servlet with the authorization header (no form login). */
    public static Map<String, Object> export(String baseUrl, String contextRoot, String authorization, String snapshotId, File target, String certificate) throws IOException {
        if (snapshotId == null || !snapshotId.matches("2064\\.[0-9a-f\\-]{36}")) throw new IllegalArgumentException("invalid snapshot id '" + snapshotId + "'");
        String servlet = trim(baseUrl) + ctx(contextRoot) + tca.baw.ProcessCenterExport.SERVLET;
        String token = request("GET", servlet + "?snapshotId=" + snapshotId + "&exportStep=prepare", null, authorization, null, certificate).trim();
        if (!token.matches("[A-Za-z0-9_]+\\|[A-Za-z0-9_.\\-]+")) throw new IOException("unexpected export prepare response from the Studio (is the context root right, does the account have access?): " + (token.length() > 200 ? token.substring(0, 200) : token).replaceAll("\\s+", " "));
        HttpURLConnection get = open("GET", servlet + "?snapshotId=" + snapshotId + "&exportStep=retrieve&exportFilePath=" + URLEncoder.encode(token, "UTF-8"), authorization, null, certificate);
        String type = String.valueOf(get.getContentType()); if (get.getResponseCode() != 200 || !type.contains("zip")) throw new IOException("export retrieve failed: HTTP " + get.getResponseCode() + " " + type);
        File tmp = new File(target.getParentFile(), target.getName() + ".part");
        try (InputStream in = get.getInputStream(); OutputStream out = new FileOutputStream(tmp)) { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n); }
        get.disconnect(); Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return Json.obj("fileName", target.getName(), "size", target.length());
    }

    static String request(String method, String url, String body, String authorization, String contentType, String certificate) throws IOException { return read(open(method, url, authorization, body, certificate), body, contentType); }
    static HttpURLConnection open(String method, String url, String authorization, String body, String certificate) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        tca.baw.Tls.apply(c, certificate);
        c.setRequestMethod(method); c.setConnectTimeout(20000); c.setReadTimeout(600000); c.setInstanceFollowRedirects(false); c.setRequestProperty("Accept", "application/json, */*"); if (authorization != null) c.setRequestProperty("Authorization", authorization);
        return c;
    }
    static String read(HttpURLConnection c, String body, String contentType) throws IOException {
        if (body != null) { c.setDoOutput(true); c.setRequestProperty("Content-Type", contentType == null ? "application/json" : contentType); byte[] b = body.getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(b.length); try (OutputStream o = c.getOutputStream()) { o.write(b); } }
        int code = c.getResponseCode(); if (code >= 300 && code < 400) throw new IOException("the Studio redirected to " + c.getHeaderField("Location") + ": the authorization was not accepted (HTTP " + code + ")");
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream(); if (in != null) { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); in.close(); } c.disconnect();
        String text = new String(bo.toByteArray(), StandardCharsets.UTF_8);
        if (code == 401 || code == 403) throw new IOException("the Studio refused the credentials (HTTP " + code + ")"); if (code >= 400) throw new IOException("the Studio answered HTTP " + code + (text.isEmpty() ? "" : ": " + (text.length() > 200 ? text.substring(0, 200) : text).replaceAll("\\s+", " ")));
        return text;
    }
    static String trim(String url) { String b = url == null ? "" : url.trim(); while (b.endsWith("/")) b = b.substring(0, b.length() - 1); return b; }
    static String ctx(String contextRoot) { if (contextRoot == null) return "/bas"; String c = contextRoot.trim(); if (c.isEmpty() || c.equals("/")) return ""; if (!c.startsWith("/")) c = "/" + c; while (c.endsWith("/")) c = c.substring(0, c.length() - 1); return c; }
}
