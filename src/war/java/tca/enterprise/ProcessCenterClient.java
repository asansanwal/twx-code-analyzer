package tca.enterprise;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;
import tca.util.Json;

/** Read access to a Process Center: the list of process applications and their snapshots (REST /rest/bpm/wle/v1/processApps, basic authentication). */
public final class ProcessCenterClient {
    private ProcessCenterClient() {}
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> apps(String baseUrl, String user, String password) throws IOException {
        String base = baseUrl.trim(); while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String body; try { body = get(base + "/rest/bpm/wle/v1/processApps", user, password, false); } catch (SSLException e) { body = get(base + "/rest/bpm/wle/v1/processApps", user, password, true); }
        return parseApps(body);
    }
    /** The processApps listing (JSON text) as applications with snapshots and tracks. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseApps(String body) throws IOException {
        Object o = Json.parse(body); if (!(o instanceof Map)) throw new IOException("unexpected answer from the Process Center");
        Map<String, Object> data = (Map<String, Object>) ((Map<String, Object>) o).get("data"); List<Map<String, Object>> out = new ArrayList<>();
        if (data == null || !(data.get("processAppsList") instanceof List)) throw new IOException("the Process Center answered without a process application list" + (((Map<String, Object>) o).get("Data") != null ? ": " + ((Map<String, Object>) o).get("Data") : ""));
        for (Object a : (List<Object>) data.get("processAppsList")) { Map<String, Object> m = (Map<String, Object>) a; List<Object> snaps = new ArrayList<>();
            Object sl = m.get("installedSnapshots") != null ? m.get("installedSnapshots") : m.get("snapshots");
            if (sl instanceof List) for (Object s : (List<Object>) sl) { Map<String, Object> sm = (Map<String, Object>) s; snaps.add(Json.obj("id", sm.get("ID"), "name", sm.get("name"), "acronym", sm.get("acronym"), "branchId", sm.get("branchID"), "branch", sm.get("branchName"), "createdOn", sm.get("createdOn"), "active", sm.get("active"), "tip", sm.get("snapshotTip"))); }
            // tracks (branches): the snapshots grouped by branch, the default track first, the tip (current working version) marked
            Map<String, Map<String, Object>> tracks = new LinkedHashMap<>(); String def = String.valueOf(m.get("defaultBranchID"));
            for (Object so : snaps) { Map<String, Object> sm = (Map<String, Object>) so; String bid = String.valueOf(sm.get("branchId")); Map<String, Object> t = tracks.get(bid); if (t == null) { t = Json.obj("id", bid, "name", sm.get("branch") == null ? "Main" : sm.get("branch"), "isDefault", bid.equals(def), "snapshots", new ArrayList<Object>()); tracks.put(bid, t); } ((List<Object>) t.get("snapshots")).add(sm); }
            List<Map<String, Object>> tl = new ArrayList<>(tracks.values()); Collections.sort(tl, new Comparator<Map<String, Object>>() { public int compare(Map<String, Object> a, Map<String, Object> b) { int c = Boolean.compare(Boolean.TRUE.equals(b.get("isDefault")), Boolean.TRUE.equals(a.get("isDefault"))); return c != 0 ? c : String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))); } });
            out.add(Json.obj("id", m.get("ID"), "name", m.get("name"), "acronym", m.get("shortName"), "description", m.get("description"), "toolkit", Boolean.TRUE.equals(m.get("isToolkit")), "branchId", m.get("defaultBranchID"), "lastModified", m.get("lastModified_on"), "tracks", tl, "snapshots", snaps)); }
        Collections.sort(out, new Comparator<Map<String, Object>>() { public int compare(Map<String, Object> a, Map<String, Object> b) { return String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))); } });
        return out;
    }
    static String get(String url, String user, String password, boolean trustAll) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (trustAll && c instanceof HttpsURLConnection) { HttpsURLConnection h = (HttpsURLConnection) c; h.setSSLSocketFactory(tca.baw.ProcessCenterExport.trustAllFactory()); h.setHostnameVerifier(new HostnameVerifier() { public boolean verify(String host, SSLSession s) { return true; } }); }
        c.setRequestMethod("GET"); c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8)));
        int code = c.getResponseCode(); InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream(); if (in != null) { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); in.close(); } c.disconnect();
        if (code == 401 || code == 403) throw new IOException("Process Center refused the credentials (HTTP " + code + ")"); if (code >= 400) throw new IOException("Process Center answered HTTP " + code);
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }
}
