package tca.enterprise;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import tca.engine.Analyzer;
import tca.web.Store;

/** Queue of analyses with a fixed worker pool; every worker has its own Analyzer. Jobs are kept in memory for an hour after they finish. */
public class Jobs {
    public static class Job {
        public final String id, user, workspace, fileName; public final long size; public final boolean toolkits; public final Captured caller; public final File storeRoot;
        public volatile String status = "queued", progress = "waiting", reportId = "", error = "", source = "upload"; public final long submitted = System.currentTimeMillis(); public volatile long started, finished;
        byte[] twx;
        Job(String id, Captured caller, Store st, String workspace, byte[] twx, String fileName, boolean toolkits) { this.id = id; this.user = caller.user == null ? "" : caller.user; this.caller = caller; this.storeRoot = st.root; this.workspace = workspace; this.twx = twx; this.fileName = fileName; this.size = twx.length; this.toolkits = toolkits; }
        public Map<String, Object> toJson(int position) { return tca.util.Json.obj("id", id, "status", status, "progress", progress, "position", position, "fileName", fileName, "size", size, "user", user, "workspace", workspace, "source", source, "reportId", reportId, "error", error, "submitted", submitted, "started", started, "finished", finished); }
    }
    public interface Runner { Map<String, Object> run(Job job, Analyzer analyzer) throws Exception; }
    final ExecutorService pool; final Map<String, Job> jobs = new LinkedHashMap<>(); final ThreadLocal<Analyzer> analyzers = new ThreadLocal<Analyzer>() { protected Analyzer initialValue() { return new Analyzer(); } };
    final Runner runner; public final int workers;
    public Jobs(int workers, Runner runner) { this.workers = Math.max(1, workers); this.runner = runner; pool = Executors.newFixedThreadPool(this.workers, new ThreadFactory() { public Thread newThread(Runnable r) { Thread t = new Thread(r, "tca-worker"); t.setDaemon(true); return t; } }); }

    public synchronized Job submit(Captured caller, Store st, String workspace, byte[] twx, String fileName, boolean toolkits, String source) {
        final Job job = new Job(new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-" + Integer.toHexString(new Random().nextInt(0xffff)), caller, st, workspace, twx, fileName, toolkits); job.source = source; jobs.put(job.id, job); prune();
        pool.submit(new Runnable() { public void run() {
            job.status = "running"; job.started = System.currentTimeMillis(); job.progress = "loading";
            Analyzer an = analyzers.get(); an.progress = new Analyzer.Progress() { public void step(String stage, int done, int total) { job.progress = stage.equals("rules") ? "rule " + done + " of " + total : stage; } };
            try { Map<String, Object> json = runner.run(job, an); job.reportId = String.valueOf(json.get("id")); job.status = "done"; job.progress = "done"; }
            catch (Throwable e) { job.status = "failed"; job.error = e instanceof tca.web.Api.ApiException || e instanceof IllegalArgumentException ? e.getMessage() : String.valueOf(e); job.progress = "failed"; }
            finally { an.progress = null; job.finished = System.currentTimeMillis(); job.twx = null; }
        } });
        return job;
    }
    public byte[] bytes(Job j) { return j.twx; }
    public synchronized Job get(String id) { return jobs.get(id); }
    /** Jobs of a workspace (newest first) with their queue position. */
    public synchronized List<Map<String, Object>> list(File storeRoot) { List<Map<String, Object>> l = new ArrayList<>(); for (Job j : jobs.values()) if (j.storeRoot.equals(storeRoot)) l.add(j.toJson(position(j))); Collections.reverse(l); return l; }
    public synchronized int position(Job j) { if (!j.status.equals("queued")) return 0; int p = 0; for (Job o : jobs.values()) { if (o == j) return p + 1; if (o.status.equals("queued")) p++; } return p; }
    public synchronized int queued() { int n = 0; for (Job j : jobs.values()) if (j.status.equals("queued")) n++; return n; }
    public synchronized int running() { int n = 0; for (Job j : jobs.values()) if (j.status.equals("running")) n++; return n; }
    void prune() { long cutoff = System.currentTimeMillis() - 3600000L; for (Iterator<Job> it = jobs.values().iterator(); it.hasNext();) { Job j = it.next(); if (j.finished > 0 && j.finished < cutoff) it.remove(); } }
}
