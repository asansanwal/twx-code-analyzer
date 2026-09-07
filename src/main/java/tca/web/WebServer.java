package tca.web;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import tca.engine.*;
import tca.model.*;
import tca.parse.TwxLoader;
import tca.rules.*;
import tca.search.Searcher;
import tca.util.Json;

/** Local web application: static UI + JSON API on the JDK HTTP server. No frameworks, no database (history = files under dataDir). */
public class WebServer {
    final Store store; final File staticDir; final Analyzer analyzer = new Analyzer();
    final Map<String, TwxModel> models = new LinkedHashMap<String, TwxModel>(16, 0.75f, true) { protected boolean removeEldestEntry(Map.Entry<String, TwxModel> e) { return size() > 4; } };
    final Map<String, RuleContext> contexts = new HashMap<>();
    final Map<String, Report> reports = new HashMap<>();

    public WebServer(File dataDir, File staticDir) { this.store = new Store(dataDir); this.staticDir = staticDir; }

    public HttpServer start(int port) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        s.createContext("/api/", new HttpHandler() { public void handle(HttpExchange x) throws IOException { try { api(x); } catch (Exception e) { e.printStackTrace(); json(x, 500, Json.obj("error", String.valueOf(e))); } } });
        s.createContext("/", new HttpHandler() { public void handle(HttpExchange x) throws IOException { serveStatic(x); } });
        s.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4)); s.start(); return s;
    }

    // ---- API --------------------------------------------------------------------------------------------------------
    void api(HttpExchange x) throws Exception {
        String path = x.getRequestURI().getPath(), method = x.getRequestMethod(); Map<String, String> q = query(x.getRequestURI().getRawQuery());
        if (path.equals("/api/rules")) { List<Object> l = new ArrayList<>(); for (Rule r : analyzer.rules()) l.add(r.toJson()); json(x, 200, l); return; }
        if (path.equals("/api/history")) { json(x, 200, store.list()); return; }
        if (path.equals("/api/analyze") && method.equals("POST")) { analyzeUpload(x, q); return; }
        if (path.startsWith("/api/report/") && path.endsWith("/pdf")) { String id = path.substring("/api/report/".length(), path.length() - 4); Report r = report(id); List<Finding> rows = PdfReport.filterFindings(r, q); bytes(x, PdfReport.findings(r, rows, PdfReport.describe(q)), "application/pdf", "report-" + r.acronym + "-" + safe(r.snapshotName) + ".pdf"); return; }
        if (path.startsWith("/api/toolkit-usage/") && path.endsWith("/pdf")) { String id = path.substring("/api/toolkit-usage/".length(), path.length() - 4); Report r = report(id); Set<String> keys = new HashSet<>(); for (String k : (q.containsKey("keys") ? q.get("keys") : "").split("\\|")) if (!k.isEmpty()) keys.add(k); @SuppressWarnings("unchecked") List<Map<String, Object>> all = (List<Map<String, Object>>) r.toolkitUsage.get("toolkits"); List<Map<String, Object>> sel = new ArrayList<>(); if (all != null) for (Map<String, Object> t : all) if (keys.isEmpty() || keys.contains(String.valueOf(t.get("key")))) sel.add(t); bytes(x, PdfReport.toolkitUsage(r, sel), "application/pdf", "toolkit-usage-" + r.acronym + "-" + safe(r.snapshotName) + ".pdf"); return; }
        if (path.startsWith("/api/report/")) { String id = path.substring("/api/report/".length()); if (method.equals("DELETE")) { store.delete(id); models.remove(id); contexts.remove(id); reports.remove(id); json(x, 200, Json.obj("deleted", id)); return; } if (!store.exists(id)) { json(x, 404, Json.obj("error", "unknown report")); return; } raw(x, 200, store.report(id), "application/json"); return; }
        if (path.startsWith("/api/objects/")) { String id = path.substring("/api/objects/".length()); RuleContext c = context(id); json(x, 200, ObjectViews.objects(c, q.containsKey("toolkits"))); return; }
        if (path.startsWith("/api/object/")) { String[] p = path.substring("/api/object/".length()).split("/", 2); RuleContext c = context(p[0]); TwxObject o = c.twx.find(p[1]); if (o == null) { json(x, 404, Json.obj("error", "unknown object")); return; } json(x, 200, ObjectViews.objectDetail(c, o, q.containsKey("xml"))); return; }
        if (path.startsWith("/api/diagram/")) { String[] p = path.substring("/api/diagram/".length()).split("/", 2); RuleContext c = context(p[0]); TwxObject o = c.twx.find(p[1]); if (o == null) { json(x, 404, Json.obj("error", "unknown object")); return; } json(x, 200, ObjectViews.diagram(c, o)); return; }
        if (path.startsWith("/api/search/")) { String id = path.substring("/api/search/".length()); RuleContext c = context(id); Set<String> types = new HashSet<>(); if (q.containsKey("types") && !q.get("types").isEmpty()) types.addAll(Arrays.asList(q.get("types").split(",")));
            json(x, 200, Searcher.search(c, q.get("q"), "1".equals(q.get("regex")), "1".equals(q.get("case")), q.containsKey("scope") ? q.get("scope") : "all", types, "1".equals(q.get("toolkits")), 500)); return; }
        if (path.startsWith("/api/toolkit-usage/")) { String id = path.substring("/api/toolkit-usage/".length()); json(x, 200, ToolkitUsage.compute(context(id))); return; }
        if (path.equals("/api/compare")) { String a = q.get("a"), b = q.get("b"); Report ra = report(a), rb = report(b); Map<String, Object> res = Diff.objects(model(a), model(b)); res.put("findings", Diff.findings(ra.findings, rb.findings)); res.put("summaryBefore", ra.toJson().get("summary")); res.put("summaryAfter", rb.toJson().get("summary")); json(x, 200, res); return; }
        json(x, 404, Json.obj("error", "unknown api " + path));
    }

    void analyzeUpload(HttpExchange x, Map<String, String> q) throws Exception {
        String ct = x.getRequestHeaders().getFirst("Content-Type"); byte[] body = readAll(x.getRequestBody()); byte[] twx = body; String fileName = q.containsKey("name") ? q.get("name") : "upload.twx";
        if (ct != null && ct.startsWith("multipart/form-data")) { Multipart.Part p = Multipart.firstFile(body, ct); if (p == null) { json(x, 400, Json.obj("error", "no file")); return; } twx = p.data; fileName = p.fileName; }
        long t0 = System.currentTimeMillis(); TwxModel m = TwxLoader.load(twx); m.fileName = fileName; Map<String, Object> settings = new HashMap<>(); if (q.containsKey("settings") && !q.get("settings").isEmpty()) { Object o = Json.parse(q.get("settings")); if (o instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) settings.put(String.valueOf(e.getKey()), e.getValue()); } settings.put("includeToolkits", "1".equals(q.get("toolkits")));
        Report r = analyzer.analyze(m, settings); String id = store.newId(); r.id = id; String json = Json.write(r.toJson());
        Map<String, Object> summary = (Map<String, Object>) r.toJson().get("summary");
        store.save(id, twx, json, Json.obj("id", id, "fileName", fileName, "fileSize", twx.length, "analyzedAt", r.analyzedAt, "app", r.appName, "acronym", r.acronym, "snapshot", r.snapshotName, "projectId", r.projectId, "objects", r.objectCount, "toolkits", r.toolkitCount, "findings", r.findings.size(), "score", summary.get("score"), "health", summary.get("healthScore"), "bySeverity", summary.get("bySeverity"), "durationMs", System.currentTimeMillis() - t0));
        models.put(id, m); contexts.put(id, new RuleContext(m, settings, Boolean.TRUE.equals(settings.get("includeToolkits")))); reports.put(id, r);
        raw(x, 200, json, "application/json");
    }

    static String safe(String s) { return s.replaceAll("[^A-Za-z0-9._-]+", "_"); }
    static void bytes(HttpExchange x, byte[] b, String ct, String fileName) throws IOException { x.getResponseHeaders().set("Content-Type", ct); x.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + fileName + "\""); x.sendResponseHeaders(200, b.length); try (OutputStream o = x.getResponseBody()) { o.write(b); } }
    TwxModel model(String id) throws Exception { TwxModel m = models.get(id); if (m == null) { m = TwxLoader.load(store.twx(id)); models.put(id, m); } return m; }
    RuleContext context(String id) throws Exception { RuleContext c = contexts.get(id); if (c == null) { c = new RuleContext(model(id), new HashMap<String, Object>(), true); contexts.put(id, c); } return c; }
    Report report(String id) throws Exception { Report r = reports.get(id); if (r == null) { r = analyzer.analyze(model(id), new HashMap<String, Object>()); r.id = id; reports.put(id, r); } return r; }


    // ---- helpers ----------------------------------------------------------------------------------------------------
    void serveStatic(HttpExchange x) throws IOException {
        String p = x.getRequestURI().getPath(); if (p.equals("/")) p = "/index.html"; if (p.contains("..")) { raw(x, 403, "forbidden", "text/plain"); return; }
        File f = new File(staticDir, p.substring(1)); if (!f.isFile()) { raw(x, 404, "not found", "text/plain"); return; }
        String ct = p.endsWith(".html") ? "text/html; charset=utf-8" : p.endsWith(".js") ? "application/javascript" : p.endsWith(".css") ? "text/css" : p.endsWith(".png") ? "image/png" : p.endsWith(".svg") ? "image/svg+xml" : p.endsWith(".woff2") ? "font/woff2" : p.endsWith(".ttf") ? "font/ttf" : "application/octet-stream";
        byte[] b = Files.readAllBytes(f.toPath()); x.getResponseHeaders().set("Content-Type", ct); x.getResponseHeaders().set("Cache-Control", p.startsWith("/vendor") || p.startsWith("/webfonts") ? "max-age=86400" : "no-cache"); x.sendResponseHeaders(200, b.length); try (OutputStream o = x.getResponseBody()) { o.write(b); }
    }
    static void json(HttpExchange x, int code, Object v) throws IOException { raw(x, code, Json.write(v), "application/json"); }
    static void raw(HttpExchange x, int code, String body, String ct) throws IOException { byte[] b = body.getBytes(StandardCharsets.UTF_8); x.getResponseHeaders().set("Content-Type", ct + (ct.startsWith("application/json") ? "; charset=utf-8" : "")); x.sendResponseHeaders(code, b.length); try (OutputStream o = x.getResponseBody()) { o.write(b); } }
    static byte[] readAll(InputStream in) throws IOException { ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); return bo.toByteArray(); }
    static Map<String, String> query(String raw) { Map<String, String> m = new HashMap<>(); if (raw == null) return m; for (String kv : raw.split("&")) { int i = kv.indexOf('='); try { m.put(java.net.URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), "UTF-8"), i < 0 ? "" : java.net.URLDecoder.decode(kv.substring(i + 1), "UTF-8")); } catch (Exception e) {} } return m; }
}
