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
        dataDir = d;
        if (flag("enterprise", "tca.enterprise", "TCA_ENTERPRISE", true)) {
            tca.enterprise.EnterpriseApi.Config c = new tca.enterprise.EnterpriseApi.Config();
            c.demo = flag("demo", "tca.demo", "TCA_DEMO", false); c.anonymous = flag("anonymous", "tca.anonymous", "TCA_ANONYMOUS", c.demo); c.signup = flag("signup", "tca.signup", "TCA_SIGNUP", true);
            c.auth = text("auth", "tca.auth", "TCA_AUTH", "builtin").toLowerCase(); c.userHeader = text("userHeader", "tca.userHeader", "TCA_USER_HEADER", ""); c.groupsHeader = text("groupsHeader", "tca.groupsHeader", "TCA_GROUPS_HEADER", ""); c.adminRole = text("adminRole", "tca.adminRole", "TCA_ADMIN_ROLE", "tca-admin"); c.adminGroup = text("adminGroup", "tca.adminGroup", "TCA_ADMIN_GROUP", "");
            for (String a : text("admins", "tca.admins", "TCA_ADMINS", "").split("[,;\\s]+")) if (!a.isEmpty()) c.admins.add(a.toLowerCase()); String af = text("adminsFile", "tca.adminsFile", "TCA_ADMINS_FILE", ""); if (!af.isEmpty()) c.adminsFile = new File(af);
            c.personalSettings = flag("personalSettings", "tca.personalSettings", "TCA_PERSONAL_SETTINGS", true); c.processCenter = flag("processCenter", "tca.processCenter", "TCA_PROCESS_CENTER", true); c.pcInlineCredentials = flag("pcInlineCredentials", "tca.pcInlineCredentials", "TCA_PC_INLINE_CREDENTIALS", true);
            c.notifications = flag("notifications", "tca.notifications", "TCA_NOTIFICATIONS", true); c.audit = flag("audit", "tca.audit", "TCA_AUDIT", true); c.keepUploads = flag("keepUploads", "tca.keepUploads", "TCA_KEEP_UPLOADS", true);
            c.workers = number("workers", "tca.workers", "TCA_WORKERS", 2); c.quotaMb = number("quotaMb", "tca.quotaMb", "TCA_QUOTA_MB", 0); c.maxUploadMb = number("maxUploadMb", "tca.maxUploadMb", "TCA_MAX_UPLOAD_MB", 512); c.maxQueue = number("maxQueue", "tca.maxQueue", "TCA_MAX_QUEUE", 20);
            for (String h : text("outboundHosts", "tca.outboundHosts", "TCA_OUTBOUND_HOSTS", "").toLowerCase().split("[,;\\s]+")) if (!h.isEmpty()) c.outboundHosts.add(h); c.outboundPrivate = flag("outboundPrivate", "tca.outboundPrivate", "TCA_OUTBOUND_PRIVATE", !c.demo); c.designerUrl = text("designerUrl", "tca.designerUrl", "TCA_DESIGNER_URL", ""); c.baseUrl = text("baseUrl", "tca.baseUrl", "TCA_BASE_URL", ""); c.title = text("title", "tca.title", "TCA_TITLE", "TWX Code Analyzer");
            tca.enterprise.EnterpriseApi e = new tca.enterprise.EnterpriseApi(new File(d), workspaces, retention, c); e.settingsReadOnly = readOnly || c.demo;
            e.notifier().smtpHost = text("smtpHost", "tca.smtpHost", "TCA_SMTP_HOST", ""); e.notifier().smtpPort = number("smtpPort", "tca.smtpPort", "TCA_SMTP_PORT", 25); e.notifier().smtpUser = text("smtpUser", "tca.smtpUser", "TCA_SMTP_USER", ""); e.notifier().smtpPassword = text("smtpPassword", "tca.smtpPassword", "TCA_SMTP_PASSWORD", ""); e.notifier().smtpFrom = text("smtpFrom", "tca.smtpFrom", "TCA_SMTP_FROM", ""); e.notifier().smtpSecurity = text("smtpSecurity", "tca.smtpSecurity", "TCA_SMTP_SECURITY", "none");
            api = e; log("TWX Code Analyzer " + tca.engine.Analyzer.VERSION + " started (enterprise" + (c.demo ? ", demo mode" : "") + "), data directory " + d + ", auth " + c.auth + ", anonymous " + c.anonymous + ", workspaces " + workspaces + ", retention " + (retention > 0 ? retention + " days (anonymous workspaces)" : "unlimited") + ", workers " + c.workers + (c.quotaMb > 0 ? ", quota " + c.quotaMb + " MB" : "") + (c.keepUploads ? "" : ", uploads discarded after analysis") + ", admins file " + e.auth.adminsFile());
        } else {
            api = new Api(new File(d), workspaces, retention); api.settingsReadOnly = readOnly; log("TWX Code Analyzer " + tca.engine.Analyzer.VERSION + " started, data directory " + d + ", workspaces " + workspaces + ", retention " + (retention > 0 ? retention + " days" : "unlimited") + (readOnly ? ", settings read-only" : ""));
        }
    }
    boolean flag(String init, String sys, String env, boolean def) { String v = param(init, sys, env); return isEmpty(v) ? def : v.trim().equalsIgnoreCase("true") || v.trim().equals("1"); }
    String text(String init, String sys, String env, String def) { String v = param(init, sys, env); return isEmpty(v) ? def : v.trim(); }
    int number(String init, String sys, String env, int def) { String v = param(init, sys, env); if (isEmpty(v)) return def; try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { log(init + " ignored: " + v); return def; } }
    String param(String init, String sys, String env) { String v = getInitParameter(init); if (isEmpty(v)) v = getServletContext().getInitParameter(init); if (isEmpty(v)) v = System.getProperty(sys); if (isEmpty(v)) v = System.getenv(env); return v; }
    static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    @Override protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String p = req.getPathInfo();
        if (p == null || p.isEmpty()) { if (!req.getRequestURI().endsWith("/")) { res.sendRedirect(req.getRequestURI() + "/"); return; } p = "/"; }   // the UI uses relative URLs: the root needs its trailing slash
        Request r = new Request(req, res, p); res.setHeader("X-Frame-Options", "SAMEORIGIN"); res.setHeader("X-Content-Type-Options", "nosniff"); res.setHeader("Referrer-Policy", "same-origin");
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
        public String remoteUser() { return req.getRemoteUser(); }
        public boolean inRole(String role) { return req.isUserInRole(role); }
        public byte[] body() throws IOException { return Api.readAll(req.getInputStream()); }
        public void send(int code, String ct, byte[] body, String disposition) throws IOException {
            res.setStatus(code); res.setContentType(ct); if (disposition != null) res.setHeader("Content-Disposition", disposition);
            res.setHeader("Cache-Control", path.startsWith("/vendor") || path.startsWith("/webfonts") ? "max-age=86400" : "no-cache");
            res.setContentLength(body.length); try (OutputStream o = res.getOutputStream()) { o.write(body); }
        }
    }
}
