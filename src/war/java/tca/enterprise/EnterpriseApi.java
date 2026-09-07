package tca.enterprise;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import tca.engine.*;
import tca.rules.*;
import tca.util.Json;
import tca.web.*;

/**
 * Enterprise layer of the WAR delivery on top of the shared {@link Api}: identity (container security, trusted header,
 * built-in accounts with sign-up / login / tokens, or anonymous demo workspaces), personal and team workspaces with roles,
 * central rule policies with quality gates and a verdict, accepted findings (baseline), an analysis queue with progress,
 * Process Center import, notifications, dashboard, executive summary, audit trail, health and metrics, storage controls,
 * and the OpenAPI description. Everything is stored as files under the data directory ({@link Directory}).
 */
public class EnterpriseApi extends Api {
    public static class Config {
        public boolean demo, anonymous = true, signup = true, personalSettings = true, processCenter = true, pcInlineCredentials = true, notifications = true, audit = true, keepUploads = true;
        public String auth = "builtin", userHeader = "", groupsHeader = "", adminRole = "tca-admin", adminGroup = "", designerUrl = "", baseUrl = "", title = "TWX Code Analyzer";
        public Set<String> admins = new HashSet<>(); public int workers = 2; public long quotaMb = 0, maxUploadMb = 512; public int maxQueue = 20; public File adminsFile;
        /** Outbound restriction for webhooks and Process Center calls: host suffixes allowed (empty = any host; link-local / metadata addresses are always refused). */
        public Set<String> outboundHosts = new HashSet<>();
        /** Whether outbound calls may target loopback, private and site-local addresses (true for intranet deployments where the Process Center lives; false on a public server). */
        public boolean outboundPrivate = true;
    }
    public static class Identity { public String user, name = "", source = "anonymous"; public boolean admin; public Set<String> groups = new HashSet<>(); public boolean anonymous() { return user == null; } }
    /** The workspace a request works in. */
    public static class Ws { public Store store; public String kind = "anonymous", id = "", label = ""; public Map<String, Object> team; public boolean manage; public Identity who; }

    public final Config cfg; final Directory dir; public final Auth auth; final Jobs jobs; final Notifier notifier = new Notifier(); final long startedAt = System.currentTimeMillis();
    final AtomicLong analysesTotal = new AtomicLong(), analysesFailed = new AtomicLong(), analysisMillis = new AtomicLong(), uploadBytes = new AtomicLong(), requests = new AtomicLong();
    final ThreadLocal<Ws> current = new ThreadLocal<>(); final ThreadLocal<Map<String, Object>> jobSource = new ThreadLocal<>();

    public EnterpriseApi(File dataDir, boolean workspaces, int retentionDays, Config cfg) {
        super(dataDir, workspaces, retentionDays); this.cfg = cfg; dir = new Directory(dataDir); auth = new Auth(dataDir, cfg.adminsFile != null ? cfg.adminsFile : new File(new File(dataDir, "enterprise"), "admins.properties"));
        jobs = new Jobs(cfg.workers, new Jobs.Runner() { public Map<String, Object> run(Jobs.Job job, Analyzer an) throws Exception { return runJob(job, an); } });
    }
    public Notifier notifier() { return notifier; }

    // ---- identity -----------------------------------------------------------------------------------------------------
    public Identity identity(Http x) {
        Identity id = new Identity();
        if (x instanceof Captured) { Captured c = (Captured) x; id.user = c.user; id.admin = c.admin; id.groups = c.groups; id.source = "job"; if (id.user != null) { Map<String, Object> u = auth.user(id.user); if (u != null) id.name = Directory.str(u, "name"); } return id; }
        String u = x.remoteUser(); if (u != null && !u.isEmpty()) { id.user = u; id.source = "container"; }
        if (id.user == null && !cfg.userHeader.isEmpty()) { String h = x.header(cfg.userHeader); if (h != null && !h.trim().isEmpty()) { id.user = h.trim(); id.source = "header"; } }
        if (id.user == null) { String a = x.header("Authorization"); if (a != null && a.startsWith("Bearer ")) { String tu = auth.tokenUser(a.substring(7).trim()); if (tu != null) { id.user = tu; id.source = "token"; } else throw new ApiException(401, "invalid or expired token"); } }
        if (id.user == null && cfg.auth.equals("builtin")) { String su = auth.sessionUser(x.cookie(Auth.COOKIE)); if (su != null) { Map<String, Object> acc = auth.user(su); if (acc != null && !Boolean.TRUE.equals(acc.get("disabled"))) { id.user = su; id.source = "session"; } } }
        if (id.user != null) { Map<String, Object> acc = auth.user(id.user); if (acc != null) id.name = Directory.str(acc, "name"); if (id.name.isEmpty()) id.name = id.user; }
        if (!cfg.groupsHeader.isEmpty()) { String g = x.header(cfg.groupsHeader); if (g != null) for (String s : g.split("[,;]")) if (!s.trim().isEmpty()) id.groups.add(s.trim()); }
        id.admin = id.user != null && (x.inRole(cfg.adminRole) || cfg.admins.contains(id.user.toLowerCase()) || auth.isAdmin(id.user) || (!cfg.adminGroup.isEmpty() && id.groups.contains(cfg.adminGroup)));
        return id;
    }
    static String wsKey(String user) { return user.toLowerCase().replaceAll("[^a-z0-9._@-]+", "_").replace("@", "_at_"); }

    // ---- workspaces ---------------------------------------------------------------------------------------------------
    public Ws workspace(Http x) {
        Ws w = current.get(); if (w != null) return w;
        Identity who = identity(x); w = new Ws(); w.who = who;
        String scope = x.cookie("tca_scope"); if (scope == null) scope = x.header("X-TCA-Workspace");
        if (!who.anonymous()) {
            if (scope != null && scope.startsWith("team:")) { Map<String, Object> t = dir.team(scope.substring(5)); if (t != null && (who.admin || Directory.isMember(t, who.user, who.groups))) { w.kind = "team"; w.id = Directory.str(t, "id"); w.label = Directory.str(t, "name"); w.team = t; w.manage = who.admin || Directory.isTeamAdmin(t, who.user); w.store = new Store(new File(new File(root, "teams"), w.id)); } }
            if (w.store == null) { w.kind = "personal"; w.id = wsKey(who.user); w.label = who.name.isEmpty() ? who.user : who.name; w.manage = true; w.store = new Store(new File(new File(root, "users"), w.id)); }
        } else {
            if (!cfg.anonymous) throw new ApiException(401, "sign in to use the analyzer");
            w.kind = workspaces ? "anonymous" : "shared"; w.label = workspaces ? "your browser" : "shared"; w.manage = true; w.store = super.store(x); w.id = w.store.root.getName();
        }
        current.set(w); return w;
    }
    @Override protected Store store(Http x) { return workspace(x).store; }
    @Override public boolean handle(Http x) throws IOException {
        String path = x.path(); if (!path.startsWith("/api/")) return false;
        requests.incrementAndGet(); current.remove();
        try {
            x.setHeader("X-Content-Type-Options", "nosniff"); x.setHeader("Cache-Control", "no-store");
            if (!x.method().equals("GET") && !x.method().equals("HEAD")) { csrfCheck(x); long len = -1; try { String cl = x.header("Content-Length"); if (cl != null) len = Long.parseLong(cl.trim()); } catch (NumberFormatException e) {} if (cfg.maxUploadMb > 0 && len > cfg.maxUploadMb * 1024L * 1024L) throw new ApiException(413, "the request exceeds the upload limit of " + cfg.maxUploadMb + " MB"); }
            if (path.startsWith("/api/auth/") || path.equals("/api/openapi.json") || path.equals("/api/health") || path.equals("/api/metrics")) { open(x); return true; }
            return super.handle(x);
        } catch (ApiException e) { json(x, e.code, Json.obj("error", e.getMessage())); return true; } finally { current.remove(); }
    }
    /** Cross-site request forgery: a state-changing request that carries a browser Origin must come from this site (bearer-token clients send no Origin). */
    static void csrfCheck(Http x) {
        String origin = x.header("Origin"); if (origin == null || origin.isEmpty() || origin.equals("null")) return;
        String host = x.header("X-Forwarded-Host"); if (host == null || host.isEmpty()) host = x.header("Host"); if (host == null) return; host = host.split(",")[0].trim();
        String oh = origin.replaceFirst("^[a-zA-Z]+://", ""); int slash = oh.indexOf('/'); if (slash >= 0) oh = oh.substring(0, slash);
        if (!oh.equalsIgnoreCase(host)) throw new ApiException(403, "cross-site request refused (origin " + origin + ")");
    }
    /** Outbound connections (webhooks, Process Center) go to http(s) hosts only, never to link-local / metadata addresses, and only to the allowed hosts when configured. */
    void checkOutbound(String url) {
        java.net.URI u; try { u = new java.net.URI(url.trim()); } catch (Exception e) { throw new ApiException(400, "invalid URL: " + url); }
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(), host = u.getHost() == null ? "" : u.getHost().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https") || host.isEmpty()) throw new ApiException(400, "only http(s) URLs with a host are allowed: " + url);
        if (host.startsWith("169.254.") || host.equals("metadata.google.internal") || host.startsWith("fe80:") || host.equals("[fe80::1]")) throw new ApiException(403, "outbound connection to " + host + " is not allowed");
        try { java.net.InetAddress a = java.net.InetAddress.getByName(host); if (a.isLinkLocalAddress()) throw new ApiException(403, "outbound connection to " + host + " is not allowed"); if (!cfg.outboundPrivate && (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isAnyLocalAddress())) throw new ApiException(403, "outbound connections to private or local addresses are not allowed on this server (" + host + ")"); } catch (java.net.UnknownHostException e) { throw new ApiException(400, "unknown host " + host); }
        if (!cfg.outboundHosts.isEmpty()) { boolean ok = false; for (String h : cfg.outboundHosts) if (host.equals(h) || host.endsWith("." + h)) ok = true; if (!ok) throw new ApiException(403, "outbound connections are restricted to " + cfg.outboundHosts + " on this server"); }
    }
    Ws ws(Http x) { workspace(x); return current.get(); }
    Captured capture(Http x) { Identity id = identity(x); Captured c = new Captured(id.user, id.admin, id.groups); String sc = x.cookie("tca_scope"); if (sc != null) c.cookies.put("tca_scope", sc); String cw = x.cookie(COOKIE); if (cw != null) c.cookies.put(COOKIE, cw); return c; }

    // ---- policies -----------------------------------------------------------------------------------------------------
    /** Policy in force for a workspace: id, name, version, settings (RuleSettings), gates. */
    @SuppressWarnings("unchecked")
    Map<String, Object> policyOf(Store st) {
        Map<String, Object> cfgw = Directory.workspaceConfig(st.root); String pid = Directory.str(cfgw, "policy"); Ws w = current.get(); boolean anonDemo = cfg.demo && (w == null || w.who.anonymous());
        if (pid.isEmpty()) pid = anonDemo || !cfg.personalSettings ? "default" : "custom";
        if (pid.equals("custom")) return Json.obj("id", "custom", "name", "Workspace settings", "version", 0, "settings", st.settings(), "gates", cfgw.get("gates") instanceof Map ? cfgw.get("gates") : new LinkedHashMap<String, Object>());
        Map<String, Object> p = pid.equals("default") ? null : dir.policy(pid);
        if (p == null) return Json.obj("id", "default", "name", "Built-in defaults", "version", 0, "settings", new RuleSettings(), "gates", cfgw.get("gates") instanceof Map ? cfgw.get("gates") : new LinkedHashMap<String, Object>());
        RuleSettings rs = RuleSettings.fromJson((Map<String, Object>) p.get("settings")).prune(analyzer.rules());
        return Json.obj("id", pid, "name", p.get("name"), "version", p.get("version"), "settings", rs, "gates", p.get("gates") instanceof Map ? p.get("gates") : new LinkedHashMap<String, Object>());
    }
    @Override protected RuleSettings settingsFor(Http x, Store st) { return (RuleSettings) policyOf(st).get("settings"); }
    @Override protected boolean canChangeSettings(Http x, Store st) { Ws w = ws(x); if (settingsReadOnly && w.who.anonymous()) return false; if (!w.manage) return false; if (w.kind.equals("personal") && !cfg.personalSettings && !w.who.admin) return false; return policyOf(st).get("id").equals("custom"); }
    @Override protected Map<String, Object> info(Http x, Store st) {
        Ws w = ws(x); Identity who = w.who; Map<String, Object> m = super.info(x, st); m.put("settingsReadOnly", !canChangeSettings(x, st));
        m.put("enterprise", true); m.put("demo", cfg.demo); m.put("title", cfg.title); m.put("auth", cfg.auth); m.put("signup", cfg.auth.equals("builtin") && cfg.signup); m.put("anonymous", cfg.anonymous);
        m.put("user", who.user); m.put("name", who.name); m.put("admin", who.admin); m.put("source", who.source);
        m.put("workspace", Json.obj("kind", w.kind, "id", w.id, "label", w.label, "manage", w.manage, "policy", policyOf(st).get("id"), "policyName", policyOf(st).get("name")));
        List<Object> teams = new ArrayList<>(); if (!who.anonymous()) for (Map<String, Object> t : dir.teamsOf(who.user, who.groups, who.admin)) teams.add(Json.obj("id", t.get("id"), "name", t.get("name"), "admin", who.admin || Directory.isTeamAdmin(t, who.user)));
        m.put("teams", teams);
        m.put("features", Json.obj("teams", !cfg.demo || who.admin, "policies", true, "processCenter", cfg.processCenter && !who.anonymous(), "pcInlineCredentials", cfg.pcInlineCredentials, "notifications", cfg.notifications && !who.anonymous(), "smtp", notifier.smtpConfigured(), "audit", cfg.audit && who.admin, "dashboard", true, "jobs", true, "gates", true, "suppressions", true, "executive", true, "remediation", true, "tokens", !who.anonymous(), "designerUrl", !cfg.designerUrl.isEmpty(), "quotaMb", cfg.quotaMb, "maxUploadMb", cfg.maxUploadMb, "keepUploads", cfg.keepUploads, "workers", jobs.workers));
        return m;
    }

    // ---- analysis hooks -------------------------------------------------------------------------------------------------
    @Override protected void beforeAnalyze(Http x, Store st, byte[] twx, String fileName) { if (cfg.maxUploadMb > 0 && twx.length > cfg.maxUploadMb * 1024L * 1024L) throw new ApiException(413, "the export exceeds the upload limit of " + cfg.maxUploadMb + " MB"); if (cfg.quotaMb > 0 && st.size() + twx.length > cfg.quotaMb * 1024L * 1024L) throw new ApiException(413, "the workspace quota of " + cfg.quotaMb + " MB is exceeded: delete older analyses first"); }
    @Override @SuppressWarnings("unchecked") protected void decorate(Http x, Store st, Report r, Map<String, Object> json, Map<String, Object> meta) {
        Map<String, Object> pol = policyOf(st); json.put("policy", Json.obj("id", pol.get("id"), "name", pol.get("name"), "version", pol.get("version"))); meta.put("policy", pol.get("id"));
        Map<String, Object> src = jobSource.get(); if (src != null) json.put("source", src);
        if (!cfg.designerUrl.isEmpty() && (src == null || Directory.str(src, "designerUrl").isEmpty())) json.put("designerUrl", cfg.designerUrl);
        json.put("uploadKept", cfg.keepUploads); meta.put("uploadKept", cfg.keepUploads); Ws w = current.get(); if (w != null) { meta.put("by", w.who.user == null ? "" : w.who.user); json.put("analyzedBy", w.who.user == null ? "" : w.who.user); }
        stamp(json, meta, Directory.suppressions(st.root), (Map<String, Object>) pol.get("gates"));
    }
    /** Marks accepted findings, recomputes the summary without them, evaluates the gate and fills the metadata. */
    @SuppressWarnings("unchecked")
    static void stamp(Map<String, Object> json, Map<String, Object> meta, Map<String, Object> suppressions, Map<String, Object> gates) {
        List<Map<String, Object>> findings = (List<Map<String, Object>>) json.get("findings"); Map<String, Object> inv = (Map<String, Object>) json.get("inventory");
        Map<String, Integer> bySev = new LinkedHashMap<>(), byCat = new TreeMap<>(), byRule = new HashMap<>(); for (String s : new String[] { "CRITICAL", "MAJOR", "MINOR", "INFO" }) bySev.put(s, 0);
        int total = 0, accepted = 0, active = 0;
        for (Map<String, Object> f : findings) {
            String key = f.get("ruleId") + "|" + f.get("objectType") + "|" + f.get("objectName") + "|" + f.get("itemName") + "|" + f.get("location"); Object sup = suppressions.get(key);
            f.remove("accepted"); f.remove("acceptedReason"); f.remove("acceptedBy"); f.remove("acceptedAt"); f.put("key", key);
            if (sup instanceof Map) { Map<String, Object> sm = (Map<String, Object>) sup; f.put("accepted", true); f.put("acceptedReason", sm.get("reason")); f.put("acceptedBy", sm.get("by")); f.put("acceptedAt", sm.get("at")); accepted++; continue; }
            active++; total += ((Number) f.get("score")).intValue(); bySev.merge(String.valueOf(f.get("severity")), 1, Integer::sum); byCat.merge(String.valueOf(f.get("category")), 1, Integer::sum); byRule.merge(String.valueOf(f.get("ruleId")), 1, Integer::sum);
        }
        int objects = inv.get("objects") instanceof Number ? ((Number) inv.get("objects")).intValue() : 0; double size = Math.max(50, objects); int health = (int) Math.max(0, Math.round(100 * Math.exp(-(total / size) / 40.0)));
        Map<String, Object> summary = (Map<String, Object>) json.get("summary"); summary.put("findings", active); summary.put("allFindings", findings.size()); summary.put("accepted", accepted); summary.put("score", total); summary.put("bySeverity", bySev); summary.put("byCategory", byCat); summary.put("healthScore", health);
        Map<String, Object> gate = evaluate(gates, summary, byRule); json.put("gate", gate); meta.put("gate", gate.get("passed"));
        meta.put("findings", active); meta.put("accepted", accepted); meta.put("score", total); meta.put("health", health); meta.put("bySeverity", bySev);
        List<Map.Entry<String, Integer>> tr = new ArrayList<>(byRule.entrySet()); Collections.sort(tr, new Comparator<Map.Entry<String, Integer>>() { public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) { return b.getValue() - a.getValue(); } });
        Map<String, Object> top = new LinkedHashMap<>(); for (int i = 0; i < Math.min(10, tr.size()); i++) top.put(tr.get(i).getKey(), tr.get(i).getValue()); meta.put("topRules", top);
    }
    /** Quality gate: every configured limit is a check; passed = all checks passed; no limits = no gate (passed null). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> evaluate(Map<String, Object> gates, Map<String, Object> summary, Map<String, Integer> byRule) {
        List<Object> checks = new ArrayList<>(); if (gates == null || Boolean.FALSE.equals(gates.get("enabled"))) return Json.obj("passed", null, "checks", checks);
        Map<String, Object> bs = (Map<String, Object>) summary.get("bySeverity"); int health = ((Number) summary.get("healthScore")).intValue();
        Object[][] defs = { { "healthMin", "Health score at least", health, true }, { "maxCritical", "Critical findings at most", bs.get("CRITICAL"), false }, { "maxMajor", "Major findings at most", bs.get("MAJOR"), false }, { "maxMinor", "Minor findings at most", bs.get("MINOR"), false }, { "maxFindings", "Open findings at most", summary.get("findings"), false }, { "maxScore", "Weighted score at most", summary.get("score"), false } };
        boolean all = true;
        for (Object[] d : defs) { Object lim = gates.get(d[0]); Integer limit = RuleSettings.intOf(lim); if (limit == null) continue; int actual = ((Number) d[2]).intValue(); boolean ok = ((Boolean) d[3]) ? actual >= limit : actual <= limit; all &= ok; checks.add(Json.obj("name", d[0], "label", d[1], "limit", limit, "actual", actual, "passed", ok)); }
        if (gates.get("rules") instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) gates.get("rules")).entrySet()) { Integer limit = RuleSettings.intOf(e.getValue()); if (limit == null) continue; int actual = byRule.containsKey(e.getKey()) ? byRule.get(e.getKey()) : 0; boolean ok = actual <= limit; all &= ok; checks.add(Json.obj("name", "rule:" + e.getKey(), "label", "Findings of " + e.getKey() + " at most", "limit", limit, "actual", actual, "passed", ok)); }
        return Json.obj("passed", checks.isEmpty() ? null : all, "checks", checks);
    }
    /** Re-applies accepted findings and the current gate to every stored report of a workspace (after suppressions or the policy changed). */
    @SuppressWarnings("unchecked")
    void restamp(Store st) throws IOException {
        Map<String, Object> sup = Directory.suppressions(st.root), gates = (Map<String, Object>) policyOf(st).get("gates");
        for (Map<String, Object> meta : st.list()) { String id = Directory.str(meta, "id"); try { Map<String, Object> json = (Map<String, Object>) Json.parse(st.report(id)); meta.remove("id"); stamp(json, meta, sup, gates); st.rewrite(id, Json.write(json), meta); forget(st, id); } catch (Exception e) { System.err.println("restamp " + id + ": " + e); } }
    }
    @Override protected void afterAnalyze(Http x, Store st, String id, Map<String, Object> json, Map<String, Object> meta) {
        analysesTotal.incrementAndGet(); analysisMillis.addAndGet(((Number) meta.get("durationMs")).longValue()); uploadBytes.addAndGet(((Number) meta.get("fileSize")).longValue());
        if (!cfg.keepUploads) st.file(id, "upload.twx").delete();
        audit(x, "analyze", Json.obj("report", id, "app", meta.get("app"), "snapshot", meta.get("snapshot"), "health", meta.get("health"), "findings", meta.get("findings"), "gate", meta.get("gate"), "source", json.get("source") == null ? "upload" : ((Map<String, Object>) json.get("source")).get("type")));
        notify(st, id, meta, json);
    }
    @Override protected void afterDelete(Http x, Store st, String id) { audit(x, "delete", Json.obj("report", id)); }
    @Override protected void settingsChanged(Http x, Store st, RuleSettings rs, String action) { audit(x, "settings." + action, Json.obj("customized", rs.isCustomized(), "rules", rs.rules.size())); try { restamp(st); } catch (IOException e) {} }

    void audit(Http x, String action, Map<String, Object> details) {
        if (!cfg.audit) return; Ws w = current.get(); Identity who = w != null ? w.who : identity(x);
        Map<String, Object> e = Json.obj("time", Directory.now(), "user", who.user == null ? "anonymous" : who.user, "workspace", w == null ? "" : w.kind + ":" + w.id, "action", action, "details", details);
        dir.audit(e); System.out.println("tca-audit " + Json.write(e));
    }
    @SuppressWarnings("unchecked")
    void notify(final Store st, final String id, final Map<String, Object> meta, Map<String, Object> json) {
        if (!cfg.notifications) return; final Map<String, Object> n = Directory.workspaceConfig(st.root).get("notify") instanceof Map ? (Map<String, Object>) Directory.workspaceConfig(st.root).get("notify") : null; if (n == null) return;
        final boolean failed = Boolean.FALSE.equals(meta.get("gate")); final boolean onComplete = Boolean.TRUE.equals(n.get("onComplete")), onGateFail = Boolean.TRUE.equals(n.get("onGateFail")); if (!(onComplete || (onGateFail && failed))) return;
        final String url = cfg.baseUrl.isEmpty() ? "" : cfg.baseUrl + (cfg.baseUrl.endsWith("/") ? "" : "/") + "#/report/" + id;
        final Map<String, Object> event = Json.obj("event", failed ? "gate.failed" : "analysis.completed", "report", id, "app", meta.get("app"), "acronym", meta.get("acronym"), "snapshot", meta.get("snapshot"), "health", meta.get("health"), "findings", meta.get("findings"), "bySeverity", meta.get("bySeverity"), "gate", json.get("gate"), "policy", json.get("policy"), "analyzedAt", meta.get("analyzedAt"), "by", meta.get("by"), "url", url);
        Thread t = new Thread(new Runnable() { public void run() {
            String hook = Directory.str(n, "webhook"); if (!hook.isEmpty()) try { checkOutbound(hook); notifier.webhook(hook, event); } catch (Exception e) { System.err.println("webhook: " + e); }
            List<String> emails = Directory.strings(n.get("emails")); if (!emails.isEmpty() && notifier.smtpConfigured()) try { notifier.mail(emails, "[TWX Code Analyzer] " + (failed ? "Quality gate FAILED: " : "Analysis completed: ") + meta.get("app") + " " + meta.get("snapshot") + " (health " + meta.get("health") + ")", "Application: " + meta.get("app") + " (" + meta.get("acronym") + ")\nSnapshot: " + meta.get("snapshot") + "\nAnalyzed: " + meta.get("analyzedAt") + "\nHealth: " + meta.get("health") + " / 100\nOpen findings: " + meta.get("findings") + " " + meta.get("bySeverity") + "\nVerdict: " + (meta.get("gate") == null ? "no gate" : Boolean.TRUE.equals(meta.get("gate")) ? "passed" : "FAILED") + (url.isEmpty() ? "" : "\nReport: " + url) + "\n"); } catch (Exception e) { System.err.println("mail: " + e); }
        } }, "tca-notify"); t.setDaemon(true); t.start();
    }

    // ---- queued analyses ------------------------------------------------------------------------------------------------
    Map<String, Object> runJob(Jobs.Job job, Analyzer an) throws Exception {
        current.remove(); current.set(workspace(job.caller)); Store st = current.get().store; if (!st.root.equals(job.storeRoot)) st = new Store(job.storeRoot);
        Map<String, Object> src = job.source.equals("upload") ? null : Json.obj("type", job.source); if (src != null && job.caller.headers.containsKey("X-Source")) src.putAll((Map<String, Object>) Json.parse(job.caller.headers.get("X-Source")));
        jobSource.set(src);
        try { return analyzeBytes(job.caller, st, jobs.bytes(job), job.fileName, job.toolkits, null, an); } catch (Exception e) { analysesFailed.incrementAndGet(); throw e; } finally { jobSource.remove(); current.remove(); }
    }

    // ---- open endpoints (no workspace): authentication, OpenAPI, health, metrics -----------------------------------------
    @SuppressWarnings("unchecked")
    void open(Http x) throws IOException {
        String path = x.path(), method = x.method();
        if (path.equals("/api/openapi.json")) { json(x, 200, OpenApi.document(cfg)); return; }
        if (path.equals("/api/health")) { json(x, 200, Json.obj("status", "ok", "version", Analyzer.VERSION, "uptimeSeconds", (System.currentTimeMillis() - startedAt) / 1000, "queued", jobs.queued(), "running", jobs.running(), "workers", jobs.workers)); return; }
        if (path.equals("/api/metrics")) { x.send(200, "text/plain; version=0.0.4; charset=utf-8", metrics().getBytes(StandardCharsets.UTF_8), null); return; }
        Map<String, Object> body = method.equals("GET") ? new HashMap<String, Object>() : bodyObject(x);
        if (path.equals("/api/auth/me")) { json(x, 200, info(x, store(x))); return; }
        if (!cfg.auth.equals("builtin")) { json(x, 404, Json.obj("error", "built-in accounts are not enabled on this server")); return; }
        try {
            if (path.equals("/api/auth/signup") && method.equals("POST")) { if (!cfg.signup) { json(x, 403, Json.obj("error", "sign-up is disabled on this server")); return; } Map<String, Object> u = auth.signup(str(body, "email"), str(body, "name"), str(body, "password")); setSession(x, auth.startSession(Directory.str(u, "id"))); dir.audit(Json.obj("time", Directory.now(), "user", u.get("id"), "action", "signup", "details", new HashMap<String, Object>())); json(x, 200, Json.obj("user", Auth.publicUser(u))); return; }
            if (path.equals("/api/auth/login") && method.equals("POST")) { Map<String, Object> u = auth.login(str(body, "email"), str(body, "password")); setSession(x, auth.startSession(Directory.str(u, "id"))); dir.audit(Json.obj("time", Directory.now(), "user", u.get("id"), "action", "login", "details", new HashMap<String, Object>())); json(x, 200, Json.obj("user", Auth.publicUser(u), "admin", auth.isAdmin(Directory.str(u, "id")))); return; }
            if (path.equals("/api/auth/logout") && method.equals("POST")) { auth.endSession(x.cookie(Auth.COOKIE)); x.setHeader("Set-Cookie", Auth.COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"); json(x, 200, Json.obj("ok", true)); return; }
            Identity who = identity(x); if (who.anonymous()) { json(x, 401, Json.obj("error", "not signed in")); return; }
            if (path.equals("/api/auth/password") && method.equals("POST")) { auth.login(who.user, str(body, "old")); auth.setPassword(who.user, str(body, "password")); json(x, 200, Json.obj("ok", true)); return; }
            if (path.equals("/api/auth/tokens")) { if (method.equals("GET")) { json(x, 200, auth.tokens(who.user)); return; } if (method.equals("POST")) { Integer days = RuleSettings.intOf(body.get("days")); json(x, 200, auth.createToken(who.user, str(body, "name"), days == null ? 365 : days)); return; } }
            if (path.startsWith("/api/auth/tokens/") && method.equals("DELETE")) { json(x, 200, Json.obj("deleted", auth.deleteToken(who.user, path.substring("/api/auth/tokens/".length())))); return; }
            if (path.equals("/api/auth/purge") && method.equals("POST")) { Store st = new Store(new File(new File(root, "users"), wsKey(who.user))); int n = 0; for (Map<String, Object> m : st.list()) { st.delete(Directory.str(m, "id")); forget(st, Directory.str(m, "id")); n++; } if (Boolean.TRUE.equals(body.get("settings"))) { new File(st.root, "settings.json").delete(); new File(st.root, "suppressions.json").delete(); new File(st.root, "workspace.json").delete(); } dir.audit(Json.obj("time", Directory.now(), "user", who.user, "action", "purge", "details", Json.obj("deleted", n))); json(x, 200, Json.obj("deleted", n)); return; }
            if (path.equals("/api/auth/account") && method.equals("DELETE")) { if (!Auth.verify(str(body, "password"), Directory.str(auth.user(who.user), "passwordHash"))) { json(x, 403, Json.obj("error", "wrong password")); return; } Store st = new Store(new File(new File(root, "users"), wsKey(who.user))); for (Map<String, Object> m : st.list()) st.delete(Directory.str(m, "id")); deleteTree(st.root); auth.forgetUser(who.user); auth.deleteUser(who.user); x.setHeader("Set-Cookie", Auth.COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"); dir.audit(Json.obj("time", Directory.now(), "user", who.user, "action", "account.delete", "details", new HashMap<String, Object>())); json(x, 200, Json.obj("deleted", true)); return; }
            // administration of accounts
            if (path.startsWith("/api/auth/users")) { if (!who.admin) { json(x, 403, Json.obj("error", "administrators only")); return; }
                if (path.equals("/api/auth/users") && method.equals("GET")) { List<Object> l = new ArrayList<>(); for (Map<String, Object> u : auth.users()) { Map<String, Object> m = Auth.publicUser(u); m.put("admin", auth.isAdmin(Directory.str(u, "id"))); m.put("workspaceBytes", new Store(new File(new File(root, "users"), wsKey(Directory.str(u, "id")))).size()); l.add(m); } json(x, 200, l); return; }
                String uid = path.substring("/api/auth/users/".length());
                if (method.equals("PUT")) { if (body.get("disabled") != null) auth.setDisabled(uid, Boolean.TRUE.equals(body.get("disabled"))); if (!str(body, "password").isEmpty()) auth.setPassword(uid, str(body, "password")); dir.audit(Json.obj("time", Directory.now(), "user", who.user, "action", "user.update", "details", Json.obj("account", uid, "disabled", body.get("disabled"), "passwordReset", !str(body, "password").isEmpty()))); json(x, 200, Json.obj("ok", true)); return; }
                if (method.equals("DELETE")) { Store st = new Store(new File(new File(root, "users"), wsKey(uid))); for (Map<String, Object> m : st.list()) st.delete(Directory.str(m, "id")); deleteTree(st.root); auth.forgetUser(uid); boolean d = auth.deleteUser(uid); dir.audit(Json.obj("time", Directory.now(), "user", who.user, "action", "user.delete", "details", Json.obj("account", uid))); json(x, 200, Json.obj("deleted", d)); return; }
            }
        } catch (IllegalArgumentException e) { json(x, 400, Json.obj("error", e.getMessage())); return; }
        json(x, 404, Json.obj("error", "unknown api " + path));
    }
    void setSession(Http x, String token) { boolean https = "https".equalsIgnoreCase(x.header("X-Forwarded-Proto")); x.setHeader("Set-Cookie", Auth.COOKIE + "=" + token + "; Path=/; Max-Age=" + (Auth.SESSION_DAYS * 86400) + "; HttpOnly; SameSite=Lax" + (https ? "; Secure" : "")); }
    static void deleteTree(File f) { File[] fs = f.listFiles(); if (fs != null) for (File c : fs) deleteTree(c); f.delete(); }
    String metrics() {
        long reports = 0, bytes = 0, users = 0, teams = 0; for (String sub : new String[] { "ws", "users", "teams" }) { File[] ds = new File(root, sub).listFiles(); if (ds == null) continue; for (File d : ds) { Store s = new Store(d); reports += s.list().size(); bytes += s.size(); if (sub.equals("users")) users++; if (sub.equals("teams")) teams++; } }
        StringBuilder b = new StringBuilder();
        b.append("# HELP tca_analyses_total Analyses completed since start\n# TYPE tca_analyses_total counter\ntca_analyses_total ").append(analysesTotal.get()).append('\n');
        b.append("# HELP tca_analyses_failed_total Analyses that failed since start\n# TYPE tca_analyses_failed_total counter\ntca_analyses_failed_total ").append(analysesFailed.get()).append('\n');
        b.append("# HELP tca_analysis_seconds_total Time spent analysing since start\n# TYPE tca_analysis_seconds_total counter\ntca_analysis_seconds_total ").append(analysisMillis.get() / 1000.0).append('\n');
        b.append("# HELP tca_upload_bytes_total Bytes uploaded since start\n# TYPE tca_upload_bytes_total counter\ntca_upload_bytes_total ").append(uploadBytes.get()).append('\n');
        b.append("# HELP tca_requests_total API requests since start\n# TYPE tca_requests_total counter\ntca_requests_total ").append(requests.get()).append('\n');
        b.append("# HELP tca_jobs_queued Analyses waiting for a worker\n# TYPE tca_jobs_queued gauge\ntca_jobs_queued ").append(jobs.queued()).append('\n');
        b.append("# HELP tca_jobs_running Analyses in progress\n# TYPE tca_jobs_running gauge\ntca_jobs_running ").append(jobs.running()).append('\n');
        b.append("# HELP tca_reports Stored analyses\n# TYPE tca_reports gauge\ntca_reports ").append(reports).append('\n');
        b.append("# HELP tca_storage_bytes Bytes used by uploads and reports\n# TYPE tca_storage_bytes gauge\ntca_storage_bytes ").append(bytes).append('\n');
        b.append("# HELP tca_user_workspaces Personal workspaces\n# TYPE tca_user_workspaces gauge\ntca_user_workspaces ").append(users).append('\n');
        b.append("# HELP tca_team_workspaces Team workspaces\n# TYPE tca_team_workspaces gauge\ntca_team_workspaces ").append(teams).append('\n');
        b.append("# HELP tca_uptime_seconds Seconds since start\n# TYPE tca_uptime_seconds gauge\ntca_uptime_seconds ").append((System.currentTimeMillis() - startedAt) / 1000).append('\n');
        return b.toString();
    }

    // ---- workspace endpoints --------------------------------------------------------------------------------------------
    @Override @SuppressWarnings("unchecked")
    protected void api(Http x, Store store) throws Exception {
        String path = x.path(), method = x.method(); Map<String, String> q = x.query(); Ws w = ws(x); Identity who = w.who;
        if (path.equals("/api/analyze") && method.equals("POST") && "1".equals(q.get("async"))) {
            String ct = x.header("Content-Type"); byte[] body = x.body(); byte[] twx = body; String fileName = q.containsKey("name") ? q.get("name") : "upload.twx";
            if (ct != null && ct.startsWith("multipart/form-data")) { Multipart.Part p = Multipart.firstFile(body, ct); if (p == null) { json(x, 400, Json.obj("error", "no file")); return; } twx = p.data; fileName = p.fileName; }
            beforeAnalyze(x, store, twx, fileName); if (jobs.queued() >= cfg.maxQueue) throw new ApiException(429, "the analysis queue is full (" + cfg.maxQueue + " waiting): try again in a few minutes"); Jobs.Job job = jobs.submit(capture(x), store, w.label, twx, fileName, "1".equals(q.get("toolkits")), "upload"); json(x, 202, job.toJson(jobs.position(job))); return;
        }
        if (path.equals("/api/analyze") && method.equals("POST") && "1".equals(q.get("gate"))) {   // synchronous upload with the verdict as the answer (pipelines): 200 passed / no gate, 422 failed
            String ct = x.header("Content-Type"); byte[] body = x.body(); byte[] twx = body; String fileName = q.containsKey("name") ? q.get("name") : "upload.twx";
            if (ct != null && ct.startsWith("multipart/form-data")) { Multipart.Part p = Multipart.firstFile(body, ct); if (p == null) { json(x, 400, Json.obj("error", "no file")); return; } twx = p.data; fileName = p.fileName; }
            Map<String, Object> rep = analyzeBytes(x, store, twx, fileName, "1".equals(q.get("toolkits")), q.get("settings"), analyzer); verdict(x, String.valueOf(rep.get("id")), rep); return;
        }
        if (path.equals("/api/jobs")) { json(x, 200, jobs.list(store.root)); return; }
        if (path.startsWith("/api/jobs/")) { Jobs.Job j = jobs.get(path.substring("/api/jobs/".length())); if (j == null || !j.storeRoot.equals(store.root)) { json(x, 404, Json.obj("error", "unknown job")); return; } json(x, 200, j.toJson(jobs.position(j))); return; }
        if (path.startsWith("/api/report/") && (path.endsWith("/gate") || path.endsWith("/verdict"))) { String id = path.substring("/api/report/".length(), path.lastIndexOf('/')); verdict(x, id, (Map<String, Object>) Json.parse(store.report(id))); return; }
        if (path.startsWith("/api/report/") && path.endsWith("/summary")) { String id = path.substring("/api/report/".length(), path.lastIndexOf('/')); Map<String, Object> rep = (Map<String, Object>) Json.parse(store.report(id)); json(x, 200, Json.obj("id", id, "app", rep.get("app"), "analyzedAt", rep.get("analyzedAt"), "summary", rep.get("summary"), "gate", rep.get("gate"), "policy", rep.get("policy"), "coverage", rep.get("coverage"), "inventory", rep.get("inventory"))); return; }
        if (path.startsWith("/api/report/") && path.endsWith("/executive.pdf")) { String id = path.substring("/api/report/".length(), path.length() - "/executive.pdf".length()); Map<String, Object> rep = (Map<String, Object>) Json.parse(store.report(id)); Map<String, Object> app = (Map<String, Object>) rep.get("app");
            List<Map<String, Object>> trend = new ArrayList<>(); for (Map<String, Object> m : store.list()) if (String.valueOf(m.get("projectId")).equals(String.valueOf(app.get("projectId"))) && !String.valueOf(m.get("projectId")).isEmpty()) trend.add(m); Collections.reverse(trend); if (trend.size() > 8) trend = trend.subList(trend.size() - 8, trend.size());
            x.send(200, "application/pdf", ExecutivePdf.render(rep, trend, w.label), "inline; filename=\"executive-" + safe(String.valueOf(app.get("acronym"))) + "-" + safe(String.valueOf(app.get("snapshot"))) + ".pdf\""); return; }
        if (path.startsWith("/api/report/") && path.endsWith("/remediation")) { String id = path.substring("/api/report/".length(), path.lastIndexOf('/')); json(x, 200, remediation((Map<String, Object>) Json.parse(store.report(id)))); return; }
        if (path.startsWith("/api/enterprise/")) { enterprise(x, store, w, who, path.substring("/api/enterprise/".length()), method, q); return; }
        super.api(x, store);
    }
    /** The verdict document of a report: HTTP 200 when passed or without a gate, 422 when the gate failed. */
    @SuppressWarnings("unchecked")
    static void verdict(Http x, String id, Map<String, Object> rep) throws IOException {
        Map<String, Object> gate = (Map<String, Object>) rep.get("gate"); Boolean passed = gate == null ? null : (Boolean) gate.get("passed");
        json(x, passed == null || passed ? 200 : 422, Json.obj("report", id, "verdict", passed == null ? "no-gate" : passed ? "passed" : "failed", "passed", passed, "checks", gate == null ? new ArrayList<Object>() : gate.get("checks"), "policy", rep.get("policy"), "summary", rep.get("summary"), "app", rep.get("app"), "analyzedAt", rep.get("analyzedAt"), "source", rep.get("source")));
    }
    /** A repository to read from: a Process Center (basic auth, form login for the export) or a Business Automation Studio (bearer / Zen). Built from a stored definition and / or the values of a request. */
    class Repo {
        String type = "pc", url = "", contextRoot = "/bas", auth = "basic", user = "", password = "", apiKey = "", token = "", designerUrl = "", connectionId = ""; String authorization;
        Repo(Map<String, Object> stored, Map<String, Object> req) {
            if (stored != null) { connectionId = Directory.str(stored, "id"); type = Directory.str(stored, "type").isEmpty() ? "pc" : Directory.str(stored, "type"); url = Directory.str(stored, "url"); contextRoot = Directory.str(stored, "contextRoot"); auth = Directory.str(stored, "auth"); user = Directory.str(stored, "user"); password = Directory.str(stored, "password"); apiKey = Directory.str(stored, "apiKey"); token = Directory.str(stored, "token"); designerUrl = Directory.str(stored, "designerUrl"); }
            if (req != null) { if (!str(req, "type").isEmpty()) type = str(req, "type").equalsIgnoreCase("studio") ? "studio" : "pc"; if (!str(req, "url").trim().isEmpty()) url = str(req, "url").trim(); if (req.get("contextRoot") != null) contextRoot = str(req, "contextRoot"); if (!str(req, "auth").isEmpty()) auth = str(req, "auth").toLowerCase();
                if (!str(req, "user").isEmpty()) { user = str(req, "user"); if (stored == null || !str(req, "password").isEmpty()) password = str(req, "password"); } else if (!str(req, "password").isEmpty()) password = str(req, "password"); if (!str(req, "apiKey").isEmpty()) apiKey = str(req, "apiKey"); if (!str(req, "token").isEmpty()) token = str(req, "token"); }
            if (type.equals("pc")) auth = "basic"; else if (auth.isEmpty()) auth = token.isEmpty() ? (apiKey.isEmpty() ? "zen" : "zen-apikey") : "bearer";
            if (url.isEmpty()) throw new ApiException(400, "the repository URL is required"); checkOutbound(url);
        }
        String authorization() throws IOException { if (authorization == null) authorization = type.equals("studio") ? Studio.authorization(url, auth.equals("zen-apikey") ? "zen" : auth, user, password, auth.equals("zen-apikey") ? apiKey : "", token) : "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)); return authorization; }
        List<Map<String, Object>> apps() throws IOException { return type.equals("studio") ? Studio.apps(url, contextRoot, authorization()) : ProcessCenterClient.apps(url, user, password); }
        void export(String snapshotId, File target) throws IOException { if (type.equals("studio")) Studio.export(url, contextRoot, authorization(), snapshotId, target); else tca.baw.ProcessCenterExport.export(url, user, password, snapshotId, target); }
        Map<String, Object> source() { return Json.obj("type", type.equals("studio") ? "studio" : "processCenter", "repository", type, "connection", connectionId, "url", url, "designerUrl", designerUrl.isEmpty() ? cfg.designerUrl : designerUrl); }
    }
    /** Findings grouped by artifact in fix order (highest open score first), with the designer link when a template is known. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> remediation(Map<String, Object> rep) {
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>(); Map<String, Object> app = (Map<String, Object>) rep.get("app"); String tpl = rep.get("designerUrl") instanceof String ? (String) rep.get("designerUrl") : rep.get("source") instanceof Map ? Directory.str((Map<String, Object>) rep.get("source"), "designerUrl") : "";
        for (Map<String, Object> f : (List<Map<String, Object>>) rep.get("findings")) { if (Boolean.TRUE.equals(f.get("accepted"))) continue; String k = f.get("objectType") + "|" + f.get("objectId") + "|" + f.get("objectName"); Map<String, Object> g = groups.get(k);
            if (g == null) { g = Json.obj("objectId", f.get("objectId"), "objectType", f.get("objectType"), "objectTypeLabel", f.get("objectTypeLabel"), "objectName", String.valueOf(f.get("objectName")).isEmpty() ? "Application" : f.get("objectName"), "package", f.get("package"), "toolkit", f.get("toolkit"), "score", 0, "count", 0, "bySeverity", new LinkedHashMap<String, Integer>(), "findings", new ArrayList<Object>(), "designerUrl", ""); groups.put(k, g);
                if (!tpl.isEmpty() && !String.valueOf(f.get("objectId")).isEmpty()) g.put("designerUrl", tpl.replace("{branchId}", String.valueOf(app.get("branchId"))).replace("{snapshotId}", String.valueOf(app.get("snapshotId"))).replace("{projectId}", String.valueOf(app.get("projectId"))).replace("{objectId}", String.valueOf(f.get("objectId")))); }
            g.put("score", ((Number) g.get("score")).intValue() + ((Number) f.get("score")).intValue()); g.put("count", ((Number) g.get("count")).intValue() + 1); ((Map<String, Integer>) g.get("bySeverity")).merge(String.valueOf(f.get("severity")), 1, Integer::sum);
            ((List<Object>) g.get("findings")).add(Json.obj("key", f.get("key"), "ruleId", f.get("ruleId"), "title", f.get("title"), "severity", f.get("severity"), "score", f.get("score"), "message", f.get("message"), "itemName", f.get("itemName"), "location", f.get("location"), "line", f.get("line"), "remediation", f.get("remediation"), "confidence", f.get("confidence"))); }
        List<Map<String, Object>> l = new ArrayList<>(groups.values()); Collections.sort(l, new Comparator<Map<String, Object>>() { public int compare(Map<String, Object> a, Map<String, Object> b) { return ((Number) b.get("score")).intValue() - ((Number) a.get("score")).intValue(); } });
        int order = 0; for (Map<String, Object> g : l) g.put("order", ++order);
        return Json.obj("artifacts", l, "designerTemplate", tpl);
    }

    @SuppressWarnings("unchecked")
    void enterprise(Http x, Store store, Ws w, Identity who, String op, String method, Map<String, String> q) throws Exception {
        Map<String, Object> body = method.equals("GET") ? new HashMap<String, Object>() : bodyObject(x);
        if (op.equals("me")) { json(x, 200, info(x, store)); return; }
        if (op.equals("dashboard")) { json(x, 200, dashboard(store)); return; }
        if (op.equals("workspace")) {
            if (method.equals("GET")) { Map<String, Object> c = Directory.workspaceConfig(store.root); c.remove("policyName"); Map<String, Object> pol = policyOf(store); c.put("policy", pol.get("id")); c.put("policyName", pol.get("name")); c.put("gates", pol.get("gates")); c.put("effectiveGates", pol.get("gates")); c.put("kind", w.kind); c.put("label", w.label); c.put("manage", w.manage); c.put("suppressions", Directory.suppressions(store.root).size()); c.put("bytes", store.size()); c.put("quotaMb", cfg.quotaMb); json(x, 200, c); return; }
            if (method.equals("PUT")) { if (!w.manage) throw new ApiException(403, "only the owner or an administrator of this workspace can change it"); if (cfg.demo && who.anonymous()) throw new ApiException(403, "sign in to configure a workspace");
                Map<String, Object> c = Directory.workspaceConfig(store.root); String pid = str(body, "policy"); if (!pid.isEmpty()) { if (!pid.equals("custom") && !pid.equals("default") && dir.policy(pid) == null) throw new ApiException(400, "unknown policy " + pid); if (pid.equals("custom") && !cfg.personalSettings && w.kind.equals("personal") && !who.admin) throw new ApiException(403, "personal rule settings are disabled on this server"); c.put("policy", pid); }
                if (body.get("gates") instanceof Map) c.put("gates", body.get("gates")); if (body.get("notify") instanceof Map) { if (!cfg.notifications) throw new ApiException(403, "notifications are disabled on this server"); String hook = Directory.str((Map<String, Object>) body.get("notify"), "webhook").trim(); if (!hook.isEmpty()) checkOutbound(hook); c.put("notify", body.get("notify")); }
                Directory.saveWorkspaceConfig(store.root, c); restamp(store); audit(x, "workspace.update", Json.obj("policy", c.get("policy"))); json(x, 200, Json.obj("saved", true)); return; }
        }
        if (op.equals("policies") || op.startsWith("policies/")) {
            if (op.equals("policies") && method.equals("GET")) { List<Object> l = new ArrayList<>(); l.add(Json.obj("id", "default", "name", "Built-in defaults", "description", "Every rule enabled with its default severity, no gate", "version", 0, "builtin", true)); if ((cfg.personalSettings && !(cfg.demo && who.anonymous())) || who.admin) l.add(Json.obj("id", "custom", "name", "Workspace settings", "description", "The rule settings edited in this workspace", "version", 0, "builtin", true)); for (Map<String, Object> p : dir.policies()) { Map<String, Object> m = new LinkedHashMap<>(p); m.remove("settings"); l.add(m); } json(x, 200, l); return; }
            if (op.equals("policies") && method.equals("POST")) { requireAdmin(who); Map<String, Object> p = dir.savePolicy(body, who.user); audit(x, "policy.save", Json.obj("policy", p.get("id"), "version", p.get("version"))); json(x, 200, p); return; }
            String pid = op.substring("policies/".length());
            if (method.equals("GET")) { Map<String, Object> p = dir.policy(pid); if (p == null) { json(x, 404, Json.obj("error", "unknown policy")); return; } Map<String, Object> m = new LinkedHashMap<>(p); m.put("document", RuleSettings.fromJson((Map<String, Object>) p.get("settings")).prune(analyzer.rules()).document(analyzer.rules())); json(x, 200, m); return; }
            requireAdmin(who);
            if (method.equals("PUT")) { body.put("id", pid); Map<String, Object> p = dir.savePolicy(body, who.user); audit(x, "policy.save", Json.obj("policy", pid, "version", p.get("version"))); json(x, 200, p); return; }
            if (method.equals("DELETE")) { boolean d = dir.deletePolicy(pid); audit(x, "policy.delete", Json.obj("policy", pid)); json(x, 200, Json.obj("deleted", d)); return; }
        }
        if (op.equals("teams") || op.startsWith("teams/")) {
            if (op.equals("teams") && method.equals("GET")) { List<Object> l = new ArrayList<>(); for (Map<String, Object> t : who.admin ? dir.teams() : dir.teamsOf(who.user, who.groups, false)) l.add(t); json(x, 200, l); return; }
            requireAdmin(who); if (cfg.demo && !who.admin) throw new ApiException(403, "teams are not available in demo mode");
            if (op.equals("teams") && method.equals("POST")) { Map<String, Object> t = dir.saveTeam(body); audit(x, "team.save", Json.obj("team", t.get("id"))); json(x, 200, t); return; }
            String tid = op.substring("teams/".length());
            if (method.equals("PUT")) { body.put("id", tid); Map<String, Object> t = dir.saveTeam(body); audit(x, "team.save", Json.obj("team", tid)); json(x, 200, t); return; }
            if (method.equals("DELETE")) { boolean d = dir.deleteTeam(tid); if (Boolean.TRUE.equals(body.get("purge")) || "1".equals(q.get("purge"))) { Store ts = new Store(new File(new File(root, "teams"), tid)); for (Map<String, Object> m : ts.list()) ts.delete(Directory.str(m, "id")); deleteTree(ts.root); } audit(x, "team.delete", Json.obj("team", tid)); json(x, 200, Json.obj("deleted", d)); return; }
        }
        if (op.equals("suppressions") || op.startsWith("suppressions")) {
            Map<String, Object> s = Directory.suppressions(store.root);
            if (method.equals("GET")) { json(x, 200, s); return; }
            if (!w.manage) throw new ApiException(403, "only the owner or an administrator of this workspace can accept findings");
            if (method.equals("PUT") || method.equals("POST")) { String key = str(body, "key"); if (key.isEmpty()) throw new ApiException(400, "key is required"); s.put(key, Json.obj("reason", str(body, "reason"), "by", who.user == null ? "anonymous" : who.user, "at", Directory.now(), "ruleId", key.substring(0, key.indexOf('|')), "path", str(body, "path"))); Directory.saveSuppressions(store.root, s); restamp(store); audit(x, "finding.accept", Json.obj("key", key, "reason", str(body, "reason"))); json(x, 200, Json.obj("saved", true, "count", s.size())); return; }
            if (method.equals("DELETE")) { String key = q.get("key"); if (key == null) throw new ApiException(400, "key is required"); Object r = s.remove(key); Directory.saveSuppressions(store.root, s); restamp(store); audit(x, "finding.unaccept", Json.obj("key", key)); json(x, 200, Json.obj("deleted", r != null, "count", s.size())); return; }
        }
        if (op.equals("audit")) { if (!who.admin || !cfg.audit) throw new ApiException(403, "administrators only"); Integer limit = RuleSettings.intOf(q.get("limit")); json(x, 200, dir.auditTail(limit == null ? 200 : Math.min(limit, 5000))); return; }
        if (op.equals("workspace/connections") || op.startsWith("workspace/connections/")) {   // Process Center definitions saved by the user in the workspace (URL, user, password)
            if (!cfg.processCenter) throw new ApiException(403, "Process Center integration is disabled on this server"); if (who.anonymous()) throw new ApiException(401, "sign in to save Process Center definitions");
            if (op.equals("workspace/connections") && method.equals("GET")) { List<Object> l = new ArrayList<>(); for (Map<String, Object> c : Directory.wsConnections(store.root)) { Map<String, Object> m = Directory.publicConnection(c); m.put("scope", "workspace"); l.add(m); } json(x, 200, l); return; }
            if (!w.manage) throw new ApiException(403, "only the owner or an administrator of this workspace can save Process Center definitions");
            if (op.equals("workspace/connections") && method.equals("POST")) { checkOutbound(str(body, "url")); body.put("savedBy", who.user); Map<String, Object> c = Directory.saveWsConnection(store.root, body); audit(x, "connection.save", Json.obj("connection", c.get("id"), "scope", "workspace")); Map<String, Object> m = Directory.publicConnection(c); m.put("scope", "workspace"); json(x, 200, m); return; }
            String wid = op.substring("workspace/connections/".length());
            if (method.equals("PUT")) { body.put("id", wid); checkOutbound(str(body, "url")); body.put("savedBy", who.user); Map<String, Object> c = Directory.saveWsConnection(store.root, body); audit(x, "connection.save", Json.obj("connection", wid, "scope", "workspace")); Map<String, Object> m = Directory.publicConnection(c); m.put("scope", "workspace"); json(x, 200, m); return; }
            if (method.equals("DELETE")) { boolean d = Directory.deleteWsConnection(store.root, wid); audit(x, "connection.delete", Json.obj("connection", wid, "scope", "workspace")); json(x, 200, Json.obj("deleted", d)); return; }
        }
        if (op.equals("connections") || op.startsWith("connections/")) {
            if (!cfg.processCenter) throw new ApiException(403, "Process Center integration is disabled on this server");
            if (op.equals("connections") && method.equals("GET")) { List<Object> l = new ArrayList<>(); if (!who.anonymous()) for (Map<String, Object> c : Directory.wsConnections(store.root)) { Map<String, Object> m = Directory.publicConnection(c); m.put("scope", "workspace"); l.add(m); } for (Map<String, Object> c : dir.connections()) { Map<String, Object> m = Directory.publicConnection(c); m.put("scope", "shared"); l.add(m); } json(x, 200, l); return; }
            if (op.endsWith("/apps") && method.equals("GET")) { if (who.anonymous()) throw new ApiException(401, "sign in to use Process Center connections"); Map<String, Object> c = connection(store, op.substring("connections/".length(), op.length() - 5)); if (c == null) { json(x, 404, Json.obj("error", "unknown connection")); return; } Map<String, Object> over = new HashMap<>(); for (String k : new String[] { "url", "user", "password", "token", "apiKey" }) if (q.containsKey(k) && !q.get(k).isEmpty()) over.put(k, q.get(k)); json(x, 200, new Repo(c, over).apps()); return; }
            requireAdmin(who);
            if (op.equals("connections") && method.equals("POST")) { checkOutbound(str(body, "url")); Map<String, Object> c = dir.saveConnection(body); audit(x, "connection.save", Json.obj("connection", c.get("id"))); json(x, 200, Directory.publicConnection(c)); return; }
            String cid = op.substring("connections/".length());
            if (method.equals("PUT")) { body.put("id", cid); checkOutbound(str(body, "url")); Map<String, Object> c = dir.saveConnection(body); audit(x, "connection.save", Json.obj("connection", cid)); json(x, 200, Directory.publicConnection(c)); return; }
            if (method.equals("DELETE")) { boolean d = dir.deleteConnection(cid); audit(x, "connection.delete", Json.obj("connection", cid)); json(x, 200, Json.obj("deleted", d)); return; }
        }
        if (op.equals("pc/apps") && method.equals("POST")) { if (!cfg.processCenter || !cfg.pcInlineCredentials) throw new ApiException(403, "Process Center access with your own credentials is disabled on this server"); if (who.anonymous()) throw new ApiException(401, "sign in to connect to a Process Center"); json(x, 200, new Repo(null, body).apps()); return; }
        if ((op.equals("pc/import") || op.equals("pc/evaluate")) && method.equals("POST")) {
            if (!cfg.processCenter) throw new ApiException(403, "Process Center integration is disabled on this server"); if (who.anonymous()) throw new ApiException(401, "sign in to import from a repository");
            String cid = str(body, "connection"); Map<String, Object> stored = null; if (!cid.isEmpty()) { stored = connection(store, cid); if (stored == null) throw new ApiException(404, "unknown connection"); } else if (!cfg.pcInlineCredentials) throw new ApiException(403, "Process Center access with your own credentials is disabled on this server");
            Repo repo = new Repo(stored, body); String url = repo.url;
            String snapshotId = str(body, "snapshotId"), appName = str(body, "app"), snapName = str(body, "snapshot"), trackName = str(body, "track");
            if (snapshotId.isEmpty()) {   // resolve by application (acronym or name) + snapshot name, or the tip of a track (default track when none is given)
                if (appName.isEmpty()) throw new ApiException(400, "snapshotId or app (+ snapshot | track) is required");
                Map<String, Object> app = null; for (Map<String, Object> a : repo.apps()) if (appName.equalsIgnoreCase(String.valueOf(a.get("acronym"))) || appName.equalsIgnoreCase(String.valueOf(a.get("name")))) app = a;
                if (app == null) throw new ApiException(404, "process application '" + appName + "' not found on the repository");
                for (Map<String, Object> t : (List<Map<String, Object>>) app.get("tracks")) { boolean trackOk = trackName.isEmpty() ? Boolean.TRUE.equals(t.get("isDefault")) || ((List<Object>) app.get("tracks")).size() == 1 : trackName.equalsIgnoreCase(String.valueOf(t.get("name"))); if (!trackOk) continue;
                    for (Map<String, Object> sn : (List<Map<String, Object>>) t.get("snapshots")) { if (snapName.isEmpty() ? Boolean.TRUE.equals(sn.get("tip")) : snapName.equalsIgnoreCase(String.valueOf(sn.get("name")))) { snapshotId = String.valueOf(sn.get("id")); snapName = String.valueOf(sn.get("name")); } } }
                if (snapshotId.isEmpty()) throw new ApiException(404, "snapshot " + (snapName.isEmpty() ? "(tip)" : "'" + snapName + "'") + " of " + appName + (trackName.isEmpty() ? "" : " track " + trackName) + " not found");
                appName = String.valueOf(app.get("acronym"));
            }
            String fileName = str(body, "fileName"); if (fileName.isEmpty()) fileName = safe(appName + "-" + snapName) + ".twx"; if (!fileName.toLowerCase().endsWith(".twx")) fileName += ".twx";
            File tmp = File.createTempFile("tca-export-", ".twx"); byte[] twx;
            try { repo.export(snapshotId, tmp); twx = java.nio.file.Files.readAllBytes(tmp.toPath()); } finally { tmp.delete(); }
            Map<String, Object> src = repo.source(); src.put("snapshotId", snapshotId); src.put("importedBy", who.user);
            beforeAnalyze(x, store, twx, fileName); Captured c = capture(x); c.headers.put("X-Source", Json.write(src));
            if (op.equals("pc/evaluate")) {   // synchronous: export, analyze, answer with the verdict (200 / 422) - one call for a pipeline
                jobSource.set(src);
                try { Map<String, Object> rep = analyzeBytes(x, store, twx, fileName, Boolean.TRUE.equals(body.get("toolkits")), null, analyzer); audit(x, "pc.evaluate", Json.obj("url", url, "snapshotId", snapshotId, "report", rep.get("id"))); verdict(x, String.valueOf(rep.get("id")), rep); } finally { jobSource.remove(); }
                return;
            }
            if (jobs.queued() >= cfg.maxQueue) throw new ApiException(429, "the analysis queue is full (" + cfg.maxQueue + " waiting): try again in a few minutes");
            Jobs.Job job = jobs.submit(c, store, w.label, twx, fileName, Boolean.TRUE.equals(body.get("toolkits")), String.valueOf(src.get("type"))); audit(x, "pc.import", Json.obj("url", url, "snapshotId", snapshotId, "job", job.id)); json(x, 202, job.toJson(jobs.position(job))); return;
        }
        json(x, 404, Json.obj("error", "unknown api /api/enterprise/" + op));
    }
    /** A Process Center definition by id: the workspace's own first, then the shared ones. */
    Map<String, Object> connection(Store st, String id) { for (Map<String, Object> c : Directory.wsConnections(st.root)) if (Directory.str(c, "id").equals(id)) return c; return dir.connection(id); }
    static void requireAdmin(Identity who) { if (!who.admin) throw new ApiException(403, "administrators only"); }

    /** Landing page data of a workspace: applications with their health trend, worst applications, most frequent rules, totals. */
    @SuppressWarnings("unchecked")
    Map<String, Object> dashboard(Store store) {
        List<Map<String, Object>> all = store.list(); Map<String, Map<String, Object>> apps = new LinkedHashMap<>(); Map<String, Integer> rules = new HashMap<>(); int gateFails = 0; double healthSum = 0; int healthN = 0;
        Collections.reverse(all);   // oldest first
        for (Map<String, Object> m : all) { String k = Directory.str(m, "projectId").isEmpty() ? Directory.str(m, "app") : Directory.str(m, "projectId"); Map<String, Object> a = apps.get(k);
            if (a == null) { a = Json.obj("key", k, "name", m.get("app"), "acronym", m.get("acronym"), "projectId", m.get("projectId"), "series", new ArrayList<Object>(), "latest", null, "first", null); apps.put(k, a); }
            Map<String, Object> pt = Json.obj("id", m.get("id"), "snapshot", m.get("snapshot"), "analyzedAt", m.get("analyzedAt"), "health", m.get("health"), "findings", m.get("findings"), "gate", m.get("gate"), "accepted", m.get("accepted"), "bySeverity", m.get("bySeverity")); ((List<Object>) a.get("series")).add(pt); if (a.get("first") == null) a.put("first", pt); a.put("latest", pt); }
        List<Object> list = new ArrayList<>();
        for (Map<String, Object> a : apps.values()) { Map<String, Object> latest = (Map<String, Object>) a.get("latest"), first = (Map<String, Object>) a.get("first"); List<Object> series = (List<Object>) a.get("series"); Map<String, Object> prev = series.size() > 1 ? (Map<String, Object>) series.get(series.size() - 2) : null;
            a.put("delta", prev == null ? null : ((Number) latest.get("health")).intValue() - ((Number) prev.get("health")).intValue()); a.put("deltaFirst", ((Number) latest.get("health")).intValue() - ((Number) first.get("health")).intValue()); a.put("analyses", series.size()); a.remove("first");
            if (Boolean.FALSE.equals(latest.get("gate"))) gateFails++; healthSum += ((Number) latest.get("health")).intValue(); healthN++;
            for (Map<String, Object> m : all) if (Directory.str(m, "id").equals(Directory.str(latest, "id")) && m.get("topRules") instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) m.get("topRules")).entrySet()) rules.merge(e.getKey(), ((Number) e.getValue()).intValue(), Integer::sum);
            list.add(a); }
        List<Map<String, Object>> worst = new ArrayList<>(); for (Object o : list) worst.add((Map<String, Object>) o); Collections.sort(worst, new Comparator<Map<String, Object>>() { public int compare(Map<String, Object> a, Map<String, Object> b) { return ((Number) ((Map<String, Object>) a.get("latest")).get("health")).intValue() - ((Number) ((Map<String, Object>) b.get("latest")).get("health")).intValue(); } });
        List<Object> worstL = new ArrayList<>(); for (int i = 0; i < Math.min(10, worst.size()); i++) { Map<String, Object> a = worst.get(i); worstL.add(Json.obj("key", a.get("key"), "name", a.get("name"), "acronym", a.get("acronym"), "latest", a.get("latest"), "delta", a.get("delta"))); }
        List<Map.Entry<String, Integer>> tr = new ArrayList<>(rules.entrySet()); Collections.sort(tr, new Comparator<Map.Entry<String, Integer>>() { public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) { return b.getValue() - a.getValue(); } });
        List<Object> topRules = new ArrayList<>(); Map<String, Rule> byId = new HashMap<>(); for (Rule r : analyzer.rules()) byId.put(r.id, r); for (int i = 0; i < Math.min(10, tr.size()); i++) { Rule r = byId.get(tr.get(i).getKey()); topRules.add(Json.obj("id", tr.get(i).getKey(), "title", r == null ? "" : r.title, "severity", r == null ? "" : r.severity.name(), "count", tr.get(i).getValue())); }
        List<Map<String, Object>> recent = store.list(); if (recent.size() > 8) recent = recent.subList(0, 8);
        return Json.obj("totals", Json.obj("analyses", all.size(), "applications", apps.size(), "averageHealth", healthN == 0 ? null : Math.round(healthSum / healthN), "gateFailures", gateFails, "bytes", store.size()), "applications", list, "worst", worstL, "topRules", topRules, "recent", recent);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> bodyObject(Http x) throws IOException { byte[] b = x.body(); if (b.length == 0) return new HashMap<>(); Object o = Json.parse(new String(b, StandardCharsets.UTF_8)); if (!(o instanceof Map)) throw new ApiException(400, "a JSON object is expected"); return (Map<String, Object>) o; }
    static String str(Map<String, Object> m, String k) { Object v = m.get(k); return v == null ? "" : String.valueOf(v); }
}
