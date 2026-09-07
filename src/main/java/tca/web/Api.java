package tca.web;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tca.engine.*;
import tca.model.*;
import tca.parse.TwxLoader;
import tca.rules.*;
import tca.search.Searcher;
import tca.util.Json;

/**
 * JSON API of the web application, shared by the desktop server ({@link WebServer}) and the servlet ({@code AnalyzerServlet}).
 * No framework, no database: the history and the rule settings live under the data directory ({@link Store}).
 * <p>
 * Workspaces (server deployments): when enabled, every browser gets a random workspace token in the cookie {@code tca_ws} and
 * sees only its own analyses and rule settings, stored under {@code <dataDir>/ws/<token>/}; deleting an analysis removes the
 * uploaded file and the report from disk. An optional retention (days) purges old analyses and workspaces once an hour.
 * The desktop app runs without workspaces: one shared history directly under the data directory.
 * <pre>
 *  GET    /api/info                       version, workspaces (true|false), retentionDays
 *  GET    /api/rules                      rule catalogue with the effective (customised) severity / impact / enabled
 *  GET    /api/settings                   full settings document (weights, thresholds, every rule) - also the export file
 *  PUT    /api/settings                   saves a settings document (full or partial, validated); returns {saved, warnings, settings}
 *  DELETE /api/settings                   back to the defaults
 *  GET    /api/history                    analysed reports (newest first)
 *  POST   /api/analyze?toolkits=0|1&name= analyses an uploaded TWX (multipart or raw body) with the stored settings (+ optional settings= overrides)
 *  GET    /api/report/{id}                stored report        DELETE /api/report/{id}   deletes the analysis permanently (report, metadata, uploaded TWX)
 *  GET    /api/report/{id}/pdf?...        findings PDF (same filters as the UI)
 *  GET    /api/objects/{id}?toolkits      object tree          GET /api/object/{id}/{objectId}?xml   GET /api/diagram/{id}/{objectId}
 *  GET    /api/search/{id}?q=&regex=&case=&scope=&types=&toolkits=
 *  GET    /api/toolkit-usage/{id}         toolkit usage        GET /api/toolkit-usage/{id}/pdf?keys=k1|k2
 *  GET    /api/compare?a=&b=              object differences and findings delta between two reports
 * </pre>
 */
public class Api {
    public static final String COOKIE = "tca_ws";
    final File root; final boolean workspaces; final int retentionDays; final Store shared; final Analyzer analyzer = new Analyzer();
    /** Demo deployments: the rule settings can be viewed and exported but not changed (PUT / DELETE /api/settings answer 403). */
    public boolean settingsReadOnly;
    final Map<String, TwxModel> models = new LinkedHashMap<String, TwxModel>(16, 0.75f, true) { protected boolean removeEldestEntry(Map.Entry<String, TwxModel> e) { return size() > 4; } };
    final Map<String, RuleContext> contexts = new HashMap<>();
    final Map<String, Report> reports = new HashMap<>();
    static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    public Api(File dataDir) { this(dataDir, false, 0); }
    public Api(File dataDir, boolean workspaces, int retentionDays) {
        this.root = dataDir; this.workspaces = workspaces; this.retentionDays = retentionDays; this.shared = new Store(dataDir);
        if (retentionDays > 0) { java.util.Timer t = new java.util.Timer("tca-retention", true); t.schedule(new java.util.TimerTask() { public void run() { try { purge(); } catch (Exception e) { e.printStackTrace(); } } }, 5000, 3600000L); }
    }

    /** Handles an /api/ request; returns false for any other path (static content is served by the transport). */
    public boolean handle(Http x) throws IOException {
        String path = x.path(); if (!path.startsWith("/api/")) return false;
        try { api(x); } catch (java.nio.file.NoSuchFileException | FileNotFoundException e) { json(x, 404, Json.obj("error", "unknown report")); } catch (Exception e) { e.printStackTrace(); json(x, 500, Json.obj("error", String.valueOf(e))); }
        return true;
    }

    /** The store of the request: the shared one, or the browser's workspace (cookie token, created on first contact). */
    Store store(Http x) {
        if (!workspaces) return shared;
        String t = x.cookie(COOKIE);
        if (t == null || !t.matches("[0-9a-f]{32}")) { byte[] b = new byte[16]; RANDOM.nextBytes(b); StringBuilder sb = new StringBuilder(); for (byte v : b) sb.append(String.format("%02x", v & 0xff)); t = sb.toString();
            boolean https = "https".equalsIgnoreCase(x.header("X-Forwarded-Proto")); x.setHeader("Set-Cookie", COOKIE + "=" + t + "; Path=/; Max-Age=31536000; HttpOnly; SameSite=Lax" + (https ? "; Secure" : "")); }
        return new Store(new File(new File(root, "ws"), t));
    }
    static String key(Store st, String id) { return st.root.getName() + "/" + id; }
    // on DELETE a workspace left empty disappears with its last analysis (see the /api/report branch)
    void forget(Store st, String id) { String k = key(st, id); models.remove(k); contexts.remove(k); reports.remove(k); }

    /** Retention: deletes analyses older than retentionDays (and workspaces left without analyses whose settings are as old). */
    void purge() {
        long cutoff = System.currentTimeMillis() - retentionDays * 86400000L; int n = 0;
        List<File> stores = new ArrayList<>(); stores.add(root); File ws = new File(root, "ws"); File[] w = ws.listFiles(); if (w != null) stores.addAll(Arrays.asList(w));
        for (File s : stores) { File[] ds = s.listFiles(); if (ds == null) continue; boolean any = false;
            for (File d : ds) { File m = new File(d, "meta.json"); if (!m.isFile()) continue; if (m.lastModified() < cutoff) { new Store(s).delete(d.getName()); models.remove(s.getName() + "/" + d.getName()); contexts.remove(s.getName() + "/" + d.getName()); reports.remove(s.getName() + "/" + d.getName()); n++; } else any = true; }
            if (s != root && !any) { File st = new File(s, "settings.json"); if (!st.isFile() || st.lastModified() < cutoff) { st.delete(); s.delete(); } }
        }
        if (n > 0) System.out.println("retention: " + n + " analysis(es) older than " + retentionDays + " days deleted");
    }

    void api(Http x) throws Exception {
        String path = x.path(), method = x.method(); Map<String, String> q = x.query(); Store store = store(x);
        if (path.equals("/api/info")) { json(x, 200, Json.obj("version", Analyzer.VERSION, "workspaces", workspaces, "retentionDays", retentionDays, "settingsReadOnly", settingsReadOnly)); return; }
        if (path.equals("/api/rules")) { RuleSettings rs = store.settings(); List<Object> l = new ArrayList<>(); for (Rule r : analyzer.rules()) { Map<String, Object> m = r.toJson(); m.put("defaultSeverity", r.severity.name()); m.put("severity", rs.severity(r).name()); m.put("impact", rs.impact(r)); m.put("enabled", rs.enabled(r)); m.put("customized", rs.hasOverride(r)); l.add(m); } json(x, 200, l); return; }
        if (path.equals("/api/settings")) {
            if (method.equals("GET")) { Map<String, Object> d = store.settings().document(analyzer.rules()); if (q.containsKey("download")) x.send(200, "application/json; charset=utf-8", Json.writePretty(d).getBytes(StandardCharsets.UTF_8), "attachment; filename=\"twx-code-analyzer-settings.json\""); else json(x, 200, d); return; }
            if (settingsReadOnly && !method.equals("GET")) { json(x, 403, Json.obj("error", "The rule settings are read-only on this server")); return; }
            if (method.equals("PUT") || method.equals("POST")) { RuleSettings rs = RuleSettings.fromJson(new String(x.body(), StandardCharsets.UTF_8)).prune(analyzer.rules()); store.saveSettings(rs); reports.clear(); contexts.clear(); json(x, 200, Json.obj("saved", true, "customized", rs.isCustomized(), "warnings", rs.warnings, "settings", rs.document(analyzer.rules()))); return; }
            if (method.equals("DELETE")) { store.saveSettings(new RuleSettings()); reports.clear(); contexts.clear(); json(x, 200, Json.obj("saved", true, "customized", false, "warnings", new ArrayList<Object>(), "settings", new RuleSettings().document(analyzer.rules()))); return; }
        }
        if (path.equals("/api/history")) { json(x, 200, store.list()); return; }
        if (path.equals("/api/analyze") && method.equals("POST")) { analyzeUpload(x, q, store); return; }
        if (path.startsWith("/api/report/") && path.endsWith("/pdf")) { String id = path.substring("/api/report/".length(), path.length() - 4); Report r = report(store, id); List<Finding> rows = PdfReport.filterFindings(r, q); x.send(200, "application/pdf", PdfReport.findings(r, rows, PdfReport.describe(q)), "inline; filename=\"report-" + r.acronym + "-" + safe(r.snapshotName) + ".pdf\""); return; }
        if (path.startsWith("/api/toolkit-usage/") && path.endsWith("/pdf")) { String id = path.substring("/api/toolkit-usage/".length(), path.length() - 4); Report r = report(store, id); Set<String> keys = new HashSet<>(); for (String k : (q.containsKey("keys") ? q.get("keys") : "").split("\\|")) if (!k.isEmpty()) keys.add(k); @SuppressWarnings("unchecked") List<Map<String, Object>> all = (List<Map<String, Object>>) r.toolkitUsage.get("toolkits"); List<Map<String, Object>> sel = new ArrayList<>(); if (all != null) for (Map<String, Object> t : all) if (keys.isEmpty() || keys.contains(String.valueOf(t.get("key")))) sel.add(t); x.send(200, "application/pdf", PdfReport.toolkitUsage(r, sel), "inline; filename=\"toolkit-usage-" + r.acronym + "-" + safe(r.snapshotName) + ".pdf\""); return; }
        if (path.startsWith("/api/report/")) { String id = path.substring("/api/report/".length()); if (method.equals("DELETE")) { boolean existed = store.exists(id); store.delete(id); forget(store, id); if (store != shared) { String[] left = store.root.list(); if (left != null && left.length == 0) store.root.delete(); } json(x, 200, Json.obj("deleted", id, "existed", existed, "permanent", true)); return; } if (!store.exists(id)) { json(x, 404, Json.obj("error", "unknown report")); return; } raw(x, 200, store.report(id), "application/json"); return; }
        if (path.startsWith("/api/objects/")) { String id = path.substring("/api/objects/".length()); RuleContext c = context(store, id); json(x, 200, ObjectViews.objects(c, q.containsKey("toolkits"))); return; }
        if (path.startsWith("/api/object/")) { String[] p = path.substring("/api/object/".length()).split("/", 2); RuleContext c = context(store, p[0]); TwxObject o = c.twx.find(p[1]); if (o == null) { json(x, 404, Json.obj("error", "unknown object")); return; } json(x, 200, ObjectViews.objectDetail(c, o, q.containsKey("xml"))); return; }
        if (path.startsWith("/api/diagram/")) { String[] p = path.substring("/api/diagram/".length()).split("/", 2); RuleContext c = context(store, p[0]); TwxObject o = c.twx.find(p[1]); if (o == null) { json(x, 404, Json.obj("error", "unknown object")); return; } json(x, 200, ObjectViews.diagram(c, o)); return; }
        if (path.startsWith("/api/search/")) { String id = path.substring("/api/search/".length()); RuleContext c = context(store, id); Set<String> types = new HashSet<>(); if (q.containsKey("types") && !q.get("types").isEmpty()) types.addAll(Arrays.asList(q.get("types").split(",")));
            json(x, 200, Searcher.search(c, q.get("q"), "1".equals(q.get("regex")), "1".equals(q.get("case")), q.containsKey("scope") ? q.get("scope") : "all", types, "1".equals(q.get("toolkits")), 500)); return; }
        if (path.startsWith("/api/toolkit-usage/")) { String id = path.substring("/api/toolkit-usage/".length()); json(x, 200, ToolkitUsage.compute(context(store, id))); return; }
        if (path.equals("/api/compare")) { String a = q.get("a"), b = q.get("b"); Report ra = report(store, a), rb = report(store, b); Map<String, Object> res = Diff.objects(model(store, a), model(store, b)); res.put("findings", Diff.findings(ra.findings, rb.findings)); res.put("summaryBefore", ra.toJson().get("summary")); res.put("summaryAfter", rb.toJson().get("summary")); json(x, 200, res); return; }
        json(x, 404, Json.obj("error", "unknown api " + path));
    }

    void analyzeUpload(Http x, Map<String, String> q, Store store) throws Exception {
        String ct = x.header("Content-Type"); byte[] body = x.body(); byte[] twx = body; String fileName = q.containsKey("name") ? q.get("name") : "upload.twx";
        if (ct != null && ct.startsWith("multipart/form-data")) { Multipart.Part p = Multipart.firstFile(body, ct); if (p == null) { json(x, 400, Json.obj("error", "no file")); return; } twx = p.data; fileName = p.fileName; }
        long t0 = System.currentTimeMillis(); TwxModel m = TwxLoader.load(twx); m.fileName = fileName;
        RuleSettings rs = store.settings(); if (q.containsKey("settings") && !q.get("settings").isEmpty()) rs = rs.mergedWith(RuleSettings.fromJson(q.get("settings")).prune(analyzer.rules()));   // request-level overrides (thresholds, rules) on top of the stored settings
        rs.includeToolkits = "1".equals(q.get("toolkits")); Map<String, Object> settings = rs.toAnalyzerSettings();
        Report r; synchronized (analyzer) { r = analyzer.analyze(m, settings); } String id = store.newId(); r.id = id; String json = Json.write(r.toJson());
        @SuppressWarnings("unchecked") Map<String, Object> summary = (Map<String, Object>) r.toJson().get("summary");
        store.save(id, twx, json, Json.obj("id", id, "fileName", fileName, "fileSize", twx.length, "analyzedAt", r.analyzedAt, "app", r.appName, "acronym", r.acronym, "snapshot", r.snapshotName, "projectId", r.projectId, "objects", r.objectCount, "toolkits", r.toolkitCount, "findings", r.findings.size(), "score", summary.get("score"), "health", summary.get("healthScore"), "bySeverity", summary.get("bySeverity"), "customized", rs.isCustomized(), "durationMs", System.currentTimeMillis() - t0));
        models.put(key(store, id), m); contexts.put(key(store, id), new RuleContext(m, settings, rs.includeToolkits)); reports.put(key(store, id), r);
        raw(x, 200, json, "application/json");
    }

    static String safe(String s) { return s.replaceAll("[^A-Za-z0-9._-]+", "_"); }
    TwxModel model(Store st, String id) throws Exception { String k = key(st, id); TwxModel m = models.get(k); if (m == null) { if (!st.exists(id)) throw new FileNotFoundException(id); m = TwxLoader.load(st.twx(id)); models.put(k, m); } return m; }
    RuleContext context(Store st, String id) throws Exception { String k = key(st, id); RuleContext c = contexts.get(k); if (c == null) { c = new RuleContext(model(st, id), st.settings().toAnalyzerSettings(), true); contexts.put(k, c); } return c; }
    /** The stored report as an object (for the PDF exports and comparisons): re-read from disk, so it reflects the settings it was analysed with. */
    Report report(Store st, String id) throws Exception { String k = key(st, id); Report r = reports.get(k); if (r == null) { if (!st.exists(id)) throw new FileNotFoundException(id); r = Report.fromJson(st.report(id)); r.id = id; reports.put(k, r); } return r; }

    static void json(Http x, int code, Object v) throws IOException { raw(x, code, Json.write(v), "application/json"); }
    static void raw(Http x, int code, String body, String ct) throws IOException { x.send(code, ct + (ct.startsWith("application/json") ? "; charset=utf-8" : ""), body.getBytes(StandardCharsets.UTF_8), null); }
    /** Content type of a static file by extension. */
    public static String contentType(String p) { return p.endsWith(".html") ? "text/html; charset=utf-8" : p.endsWith(".js") ? "application/javascript" : p.endsWith(".css") ? "text/css" : p.endsWith(".png") ? "image/png" : p.endsWith(".svg") ? "image/svg+xml" : p.endsWith(".woff2") ? "font/woff2" : p.endsWith(".ttf") ? "font/ttf" : p.endsWith(".json") ? "application/json" : "application/octet-stream"; }
    public static Map<String, String> query(String raw) { Map<String, String> m = new HashMap<>(); if (raw == null) return m; for (String kv : raw.split("&")) { int i = kv.indexOf('='); try { m.put(java.net.URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), "UTF-8"), i < 0 ? "" : java.net.URLDecoder.decode(kv.substring(i + 1), "UTF-8")); } catch (Exception e) {} } return m; }
    public static byte[] readAll(InputStream in) throws IOException { ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); return bo.toByteArray(); }
}
