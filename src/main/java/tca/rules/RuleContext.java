package tca.rules;

import java.util.*;
import tca.model.*;
import tca.parse.*;

/** Parsed view of the TWX shared by all rules (models are parsed once and cached). */
public class RuleContext {
    public final TwxModel twx;
    public final Map<String, Object> settings;
    public final boolean includeToolkits;
    private final Map<String, ServiceModel> services = new HashMap<>();
    private final Map<String, BpdModel> bpds = new HashMap<>();
    private final Map<String, CoachViewModel> coachViews = new HashMap<>();
    private final Map<String, BusinessObjectModel> bos = new HashMap<>();
    private final Map<String, List<Script>> scripts = new HashMap<>();
    private Map<String, Set<String>> referencedBy;
    private final Map<String, Set<String>> declared = new HashMap<>();
    /** Rule-private caches (parsed ASTs ...), keyed by the rule set. */
    public final Map<String, Object> cache = new HashMap<>();
    /** Analysis coverage counters filled by the script rules: scripts parsed, scripts with syntax errors (skipped by the AST rules). */
    public int scriptsParsed, scriptsWithSyntaxErrors;
    public RuleContext(TwxModel twx, Map<String, Object> settings, boolean includeToolkits) { this.twx = twx; this.settings = settings == null ? new HashMap<String, Object>() : settings; this.includeToolkits = includeToolkits; }

    public int threshold(String name, int def) { Object v = settings.get(name); if (v instanceof Number) return ((Number) v).intValue(); if (v instanceof String) try { return Integer.parseInt((String) v); } catch (NumberFormatException e) {} return def; }
    /** Objects to analyse: the application only, or with the non-system toolkits. */
    public List<TwxObject> objects(String type) {
        List<TwxObject> l = new ArrayList<>();
        for (TwxPackage p : packages()) for (TwxObject o : p.objects.values()) if (type == null || o.type.equals(type)) l.add(o);
        return l;
    }
    public List<TwxPackage> packages() {
        List<TwxPackage> l = new ArrayList<>(); l.add(twx.app);
        if (includeToolkits) for (TwxPackage p : twx.toolkits.values()) if (!isSystem(p)) l.add(p);
        return l;
    }
    public boolean isSystem(TwxPackage p) { String a = p.acronym; return a.startsWith("SYS") || a.equals("TWSYS") || a.equals("BPMUI") || a.equals("SYSC") || a.equals("SYSRC") || a.equals("SYSCM") || a.equals("SYSD") || a.equals("SYSCP") || a.equals("SYSRTC") || a.equals("SYSREST") || a.equals("SYSDC"); }
    public ServiceModel service(TwxObject o) { ServiceModel s = services.get(o.id); if (s == null) { s = ServiceParser.parse(o); services.put(o.id, s); } return s; }
    public BpdModel bpd(TwxObject o) { BpdModel b = bpds.get(o.id); if (b == null) { b = BpdParser.parse(o); bpds.put(o.id, b); } return b; }
    public CoachViewModel coachView(TwxObject o) { CoachViewModel c = coachViews.get(o.id); if (c == null) { c = CoachViewParser.parse(o); coachViews.put(o.id, c); } return c; }
    public BusinessObjectModel bo(TwxObject o) { BusinessObjectModel b = bos.get(o.id); if (b == null) { b = BusinessObjectParser.parse(o); bos.put(o.id, b); } return b; }
    public List<Script> scripts(TwxObject o) {
        List<Script> l = scripts.get(o.id);
        if (l == null) { l = Scripts.of(o, o.type.equals("process") ? service(o) : null, o.type.equals("bpd") ? bpd(o) : null, o.type.equals("coachView") ? coachView(o) : null); scripts.put(o.id, l); }
        return l;
    }
    /** Names usable as tw.local.<name> in the scripts of a service / process: parameters and private variables. */
    public Set<String> declaredVariables(TwxObject o) {
        Set<String> s = declared.get(o.id);
        if (s == null) {
            s = new HashSet<>();
            if (o.type.equals("process")) { for (ServiceModel.Variable v : service(o).parameters) s.add(v.name); for (ServiceModel.Variable v : service(o).variables) s.add(v.name); }
            else if (o.type.equals("bpd")) { for (ServiceModel.Variable v : bpd(o).parameters) s.add(v.name); for (ServiceModel.Variable v : bpd(o).variables) s.add(v.name); TwxObject parent = embeddingBpd(o); if (parent != null) s.addAll(declaredVariables(parent)); }   // an embedded (sub)process shares the variables of the process that contains it
            declared.put(o.id, s);
        }
        return s;
    }
    /** The process that embeds this BPD as a subprocess (activity with embeddedProcessId), or null for a top-level process. */
    public TwxObject embeddingBpd(TwxObject o) { String ref = "<embeddedProcessId>" + o.id + "</embeddedProcessId>"; for (TwxObject b : o.pkg.byType("bpd")) if (b != o && b.xml.contains(ref)) return b; return null; }
    public List<Script> allScripts() { List<Script> l = new ArrayList<>(); for (TwxObject o : objects(null)) if (o.type.equals("process") || o.type.equals("bpd") || o.type.equals("coachView")) l.addAll(scripts(o)); return l; }
    /** Reverse reference index over every package: object id -> ids of objects referencing it. */
    public Map<String, Set<String>> referencedBy() {
        if (referencedBy == null) {
            referencedBy = new HashMap<>();
            for (TwxPackage p : twx.allPackages()) for (TwxObject o : p.objects.values()) for (String r : o.refs()) { Set<String> s = referencedBy.get(r); if (s == null) { s = new HashSet<>(); referencedBy.put(r, s); } s.add(o.id); }
        }
        return referencedBy;
    }
    public Set<String> referencedBy(String id) { Set<String> s = referencedBy().get(id); return s == null ? Collections.<String>emptySet() : s; }
    /** Name of a referenced object (service, BO, team ...) for messages. */
    public String nameOf(String ref) { TwxObject o = twx.find(ref); return o == null ? ref : o.name; }
    public String typeName(String classRef) { TwxObject o = twx.find(classRef); if (o != null) return o.name; if (classRef == null) return ""; String bare = classRef.contains("/") ? classRef.substring(classRef.lastIndexOf('/') + 1) : classRef; return SYSTEM_TYPES.containsKey(bare) ? SYSTEM_TYPES.get(bare) : bare; }
    static final Map<String, String> SYSTEM_TYPES = new HashMap<>();
    static { SYSTEM_TYPES.put("12.db884a3c-c533-44b7-bb2d-47bec8ad4022", "String"); SYSTEM_TYPES.put("12.3fa0d7a0-828a-4d60-99cc-db5ed143fc2d", "Integer"); SYSTEM_TYPES.put("12.536b2aa5-a30f-4eca-87fa-3a28066753ee", "Decimal"); SYSTEM_TYPES.put("12.83ff975e-8dbc-42e5-b738-fa8bc08274a2", "Boolean"); SYSTEM_TYPES.put("12.68474ab0-d56f-47ee-b7e9-510b45a2a8be", "Date"); SYSTEM_TYPES.put("12.c09c9b6e-aabd-4897-bef2-ed61db106297", "ANY"); SYSTEM_TYPES.put("12.d2e5a15a-ea53-4793-9e93-29af5bd80b13", "NameValuePair"); SYSTEM_TYPES.put("12.06fc5986-1156-4005-ac19-1d0b535b7fce", "Map"); }
}
