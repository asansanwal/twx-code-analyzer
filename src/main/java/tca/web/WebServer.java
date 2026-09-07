package tca.web;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Desktop web application: the JDK HTTP server serving the static UI from a folder and the JSON API ({@link Api}). Binds to 127.0.0.1 unless a host is given. */
public class WebServer {
    final Api api; final File staticDir;
    public WebServer(File dataDir, File staticDir) { this(dataDir, staticDir, false, 0); }
    public WebServer(File dataDir, File staticDir, boolean workspaces, int retentionDays) { this(dataDir, staticDir, workspaces, retentionDays, false); }
    public WebServer(File dataDir, File staticDir, boolean workspaces, int retentionDays, boolean settingsReadOnly) { this.api = new Api(dataDir, workspaces, retentionDays); this.api.settingsReadOnly = settingsReadOnly; this.staticDir = staticDir; }

    public HttpServer start(int port) throws IOException { return start("127.0.0.1", port); }
    public HttpServer start(String host, int port) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress(host, port), 0);
        s.createContext("/", new HttpHandler() { public void handle(HttpExchange x) throws IOException { Exchange e = new Exchange(x); if (!api.handle(e)) serveStatic(e); } });
        s.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4)); s.start(); return s;
    }

    void serveStatic(Http x) throws IOException {
        String p = x.path(); if (p.equals("/")) p = "/index.html"; if (p.contains("..")) { x.send(403, "text/plain", "forbidden".getBytes("UTF-8"), null); return; }
        File f = new File(staticDir, p.substring(1)); if (!f.isFile()) { x.send(404, "text/plain", "not found".getBytes("UTF-8"), null); return; }
        x.send(200, Api.contentType(p), Files.readAllBytes(f.toPath()), null);
    }

    /** JDK HttpExchange as an {@link Http}. */
    static class Exchange implements Http {
        final HttpExchange x; Exchange(HttpExchange x) { this.x = x; }
        public String method() { return x.getRequestMethod(); }
        public String path() { return x.getRequestURI().getPath(); }
        public Map<String, String> query() { return Api.query(x.getRequestURI().getRawQuery()); }
        public String header(String name) { return x.getRequestHeaders().getFirst(name); }
        public String cookie(String name) { List<String> hs = x.getRequestHeaders().get("Cookie"); if (hs != null) for (String h : hs) for (String c : h.split(";")) { int i = c.indexOf('='); if (i > 0 && c.substring(0, i).trim().equals(name)) return c.substring(i + 1).trim(); } return null; }
        public void setHeader(String name, String value) { x.getResponseHeaders().add(name, value); }
        public String remoteUser() { return null; }
        public boolean inRole(String role) { return false; }
        public byte[] body() throws IOException { return Api.readAll(x.getRequestBody()); }
        public void send(int code, String ct, byte[] body, String disposition) throws IOException {
            x.getResponseHeaders().set("Content-Type", ct); if (disposition != null) x.getResponseHeaders().set("Content-Disposition", disposition);
            String p = path(); x.getResponseHeaders().set("Cache-Control", p.startsWith("/vendor") || p.startsWith("/webfonts") ? "max-age=86400" : "no-cache");
            x.sendResponseHeaders(code, body.length); try (OutputStream o = x.getResponseBody()) { o.write(body); }
        }
    }
}
