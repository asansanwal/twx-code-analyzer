package tca.web;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * Servlet front of the analyzer for the WAR delivery (WebSphere Liberty, WebSphere traditional, Tomcat ...): the JSON API
 * ({@link Api}) plus the static UI packaged in the WAR. The data directory (history, uploaded TWX files, rule settings) is
 * taken from, in this order: the servlet init parameter / context parameter {@code dataDir}, the system property
 * {@code tca.data}, the environment variable {@code TCA_DATA}, else {@code <java.io.tmpdir>/twx-code-analyzer}.
 * {@code workspaces} (default true: every browser sees only its own analyses and settings) and {@code retentionDays}
 * (default 0 = keep everything) and {@code settingsReadOnly} (default false; demo servers: rule settings can be viewed and exported but not changed)
 * are resolved the same way ({@code tca.workspaces} / {@code TCA_WORKSPACES}, {@code tca.retentionDays} / {@code TCA_RETENTION_DAYS}, {@code tca.settingsReadOnly} / {@code TCA_SETTINGS_READONLY}).
 * The Jakarta variant of this class ({@code jakarta.servlet} packages) is generated at build time from this source.
 */
public class AnalyzerServlet extends HttpServlet {
    private Api api; private String dataDir;

    @Override public void init() throws ServletException {
        String d = param("dataDir", "tca.data", "TCA_DATA"); if (isEmpty(d)) d = new File(System.getProperty("java.io.tmpdir"), "twx-code-analyzer").getAbsolutePath();
        String w = param("workspaces", "tca.workspaces", "TCA_WORKSPACES"), r = param("retentionDays", "tca.retentionDays", "TCA_RETENTION_DAYS");
        boolean workspaces = isEmpty(w) || w.trim().equalsIgnoreCase("true") || w.trim().equals("1"); int retention = 0; if (!isEmpty(r)) try { retention = Integer.parseInt(r.trim()); } catch (NumberFormatException e) { log("retentionDays ignored: " + r); }
        String ro = param("settingsReadOnly", "tca.settingsReadOnly", "TCA_SETTINGS_READONLY"); boolean readOnly = !isEmpty(ro) && (ro.trim().equalsIgnoreCase("true") || ro.trim().equals("1"));
        dataDir = d; api = new Api(new File(d), workspaces, retention); api.settingsReadOnly = readOnly; log("TWX Code Analyzer " + tca.engine.Analyzer.VERSION + " started, data directory " + d + ", workspaces " + workspaces + ", retention " + (retention > 0 ? retention + " days" : "unlimited") + (readOnly ? ", settings read-only" : ""));
    }
    String param(String init, String sys, String env) { String v = getInitParameter(init); if (isEmpty(v)) v = getServletContext().getInitParameter(init); if (isEmpty(v)) v = System.getProperty(sys); if (isEmpty(v)) v = System.getenv(env); return v; }
    static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    @Override protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String p = req.getPathInfo();
        if (p == null || p.isEmpty()) { if (!req.getRequestURI().endsWith("/")) { res.sendRedirect(req.getRequestURI() + "/"); return; } p = "/"; }   // the UI uses relative URLs: the root needs its trailing slash
        Request r = new Request(req, res, p);
        if (api.handle(r)) return;
        if (p.equals("/")) p = "/index.html";
        if (p.contains("..") || p.startsWith("/WEB-INF") || p.startsWith("/META-INF")) { r.send(403, "text/plain", "forbidden".getBytes("UTF-8"), null); return; }
        InputStream in = getServletContext().getResourceAsStream(p); if (in == null) { r.send(404, "text/plain", "not found".getBytes("UTF-8"), null); return; }
        try { r.send(200, Api.contentType(p), Api.readAll(in), null); } finally { in.close(); }
    }

    /** Servlet request / response pair as an {@link Http}. */
    static class Request implements Http {
        final HttpServletRequest req; final HttpServletResponse res; final String path;
        Request(HttpServletRequest req, HttpServletResponse res, String path) { this.req = req; this.res = res; this.path = path; }
        public String method() { return req.getMethod(); }
        public String path() { return path; }
        public Map<String, String> query() { return Api.query(req.getQueryString()); }
        public String header(String name) { return req.getHeader(name); }
        public String cookie(String name) { Cookie[] cs = req.getCookies(); if (cs != null) for (Cookie c : cs) if (c.getName().equals(name)) return c.getValue(); return null; }
        public void setHeader(String name, String value) { res.addHeader(name, value); }
        public byte[] body() throws IOException { return Api.readAll(req.getInputStream()); }
        public void send(int code, String ct, byte[] body, String disposition) throws IOException {
            res.setStatus(code); res.setContentType(ct); if (disposition != null) res.setHeader("Content-Disposition", disposition);
            res.setHeader("Cache-Control", path.startsWith("/vendor") || path.startsWith("/webfonts") ? "max-age=86400" : "no-cache");
            res.setContentLength(body.length); try (OutputStream o = res.getOutputStream()) { o.write(body); }
        }
    }
}
