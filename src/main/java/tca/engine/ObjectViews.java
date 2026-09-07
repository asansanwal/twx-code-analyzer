package tca.engine;

import java.util.*;
import tca.diagram.DiagramBuilder;
import tca.model.*;
import tca.rules.RuleContext;
import tca.util.Json;
import tca.util.Xml;

/** JSON views of the loaded TWX used by both deliveries (local web app and BAW process app): object tree, object detail, diagram. */
public final class ObjectViews {
    private ObjectViews() {}

    /** Packages with their objects (application only, or every bundled toolkit as well). */
    public static Map<String, Object> objects(RuleContext c, boolean toolkits) {
        List<Object> pk = new ArrayList<>();
        for (TwxPackage p : toolkits ? c.twx.allPackages() : Collections.singletonList(c.twx.app)) {
            List<Object> objs = new ArrayList<>();
            for (TwxObject o : p.objects.values()) {
                if (o.type.equals("artifact") || o.type.equals("contribution")) continue;
                Map<String, Object> row = Json.obj("id", o.id, "name", o.name, "type", o.type, "typeLabel", o.typeLabel());
                if (o.type.equals("process")) { String st = c.service(o).typeLabel(); row.put("subtype", st); row.put("typeLabel", st); }   // services are grouped by their kind (CSHS, Ajax, integration ...)
                objs.add(row);
            }
            pk.add(Json.obj("name", p.name, "acronym", p.acronym, "snapshot", p.snapshotName, "toolkit", p.toolkit, "system", c.isSystem(p), "objects", objs));
        }
        return Json.obj("packages", pk);
    }

    /** Detail of one object: references both ways, scripts, coach view options, business object properties, optionally the raw XML. */
    public static Map<String, Object> objectDetail(RuleContext c, TwxObject o, boolean xml) {
        Map<String, Object> m = Json.obj("id", o.id, "name", o.name, "type", o.type, "typeLabel", o.typeLabel(), "package", o.pkg.label(), "versionId", o.versionId, "size", o.xml.length());
        List<Object> refs = new ArrayList<>(); for (String r : o.refs()) { TwxObject t = c.twx.find(r); if (t != null) refs.add(ref(t)); }
        List<Object> used = new ArrayList<>(); for (String r : c.referencedBy(o.id)) { TwxObject t = c.twx.find(r); if (t != null) used.add(ref(t)); }
        m.put("references", refs); m.put("referencedBy", used);
        List<Object> sc = new ArrayList<>();
        if (o.type.equals("process") || o.type.equals("bpd") || o.type.equals("coachView")) for (Script s : c.scripts(o)) sc.add(Json.obj("location", s.location, "itemId", s.itemId, "kind", s.kind, "lines", s.lines(), "code", s.code));
        m.put("scripts", sc);
        if (o.type.equals("coachView")) {
            CoachViewModel v = c.coachView(o); List<Object> ops = new ArrayList<>();
            for (CoachViewModel.Option op : v.options) ops.add(Json.obj("name", op.name, "label", op.label, "type", op.propertyType, "list", op.isList, "class", c.typeName(op.classRef), "description", op.description));
            m.put("options", ops); m.put("binding", c.typeName(v.bindingType) + (v.bindingList ? "[]" : "")); m.put("resources", v.resources);
        }
        if (o.type.equals("twClass")) {
            BusinessObjectModel b = c.bo(o); List<Object> ps = new ArrayList<>();
            for (BusinessObjectModel.Property p : b.properties) ps.add(Json.obj("name", p.name, "type", c.typeName(p.classRef) + (p.isList ? "[]" : ""), "documentation", p.documentation));
            m.put("properties", ps); m.put("shared", b.shared); m.put("schema", schema(c, o, new ArrayDeque<String>(), 0));
        }
        if (o.type.equals("process") || o.type.equals("bpd")) { m.put("variables", variables(c, o)); m.put("elements", elements(c, o)); }
        if (o.type.equals("managedAsset")) { String code = Xml.text(o.xml, "assetTypeCode"); m.put("assetType", code.equals("J") ? "Server file" : code.equals("W") ? "Web file" : code.equals("D") ? "Design file" : code); m.put("mimeType", Xml.text(o.xml, "mimeType")); m.put("length", Xml.integer(o.xml, "length", 0)); if (o.pkg.jsFiles.containsKey(o.id)) m.put("content", o.pkg.jsFiles.get(o.id)); }
        if (xml) m.put("xml", o.xml);
        return m;
    }

    /** Parameters and private variables of a service or process: name, kind, type, default, documentation. */
    public static List<Object> variables(RuleContext c, TwxObject o) {
        List<ServiceModel.Variable> all = new ArrayList<>();
        if (o.type.equals("process")) { all.addAll(c.service(o).parameters); all.addAll(c.service(o).variables); } else { all.addAll(c.bpd(o).parameters); all.addAll(c.bpd(o).variables); }
        List<Object> l = new ArrayList<>();
        for (ServiceModel.Variable v : all) l.add(Json.obj("name", v.name, "kind", v.kind, "type", c.typeName(v.classRef) + (v.isList ? "[]" : ""), "hasDefault", v.hasDefault, "default", v.defaultValue, "documentation", v.documentation));
        return l;
    }
    /** Flat list of the steps of a service or the flow objects of a process (name, kind, implementation, lane, pre/post assignments, script size). */
    public static List<Object> elements(RuleContext c, TwxObject o) {
        List<Object> l = new ArrayList<>();
        if (o.type.equals("process")) for (ServiceModel.Item it : c.service(o).items) {
            TwxObject t = it.attachedRef.isEmpty() ? null : c.twx.find(it.attachedRef);
            l.add(Json.obj("id", it.id, "name", it.name, "kind", it.kind(), "component", it.component, "attachedRef", it.attachedRef, "attachedName", t != null ? t.name : it.attachedName, "attachedType", t != null ? t.typeLabel() : "", "pre", it.preAssignments, "post", it.postAssignments, "mappings", it.mappings.size(), "scriptLines", it.script.trim().isEmpty() ? 0 : it.script.split("\\n").length, "template", it.isTemplate(), "errorHandler", it.errorHandlerEnabled, "documentation", it.documentation));
        } else for (BpdModel.FlowObject f : c.bpd(o).flowObjects) {
            TwxObject t = f.attachedRef.isEmpty() ? null : c.twx.find(f.attachedRef);
            l.add(Json.obj("id", f.id, "name", f.name, "kind", f.kind(), "component", f.componentType, "lane", f.laneName, "systemLane", f.systemLane, "attachedRef", f.attachedRef, "attachedName", t != null ? t.name : f.attachedName, "attachedType", t != null ? t.typeLabel() : "", "mappings", f.mappings.size(), "multiInstance", f.multiInstance, "errorHandling", f.hasErrorHandling, "event", f.isErrorEvent() ? "error" : f.isTimerEvent() ? "timer" : f.isMessageEvent() ? "message" : "", "documentation", f.documentation));
        }
        return l;
    }
    /** Business object properties with the nested structure of complex types resolved (depth-limited, cycles flagged as circular, unknown types as unresolved). */
    public static List<Object> schema(RuleContext c, TwxObject bo, Deque<String> stack, int depth) {
        List<Object> l = new ArrayList<>(); stack.push(bo.id);
        for (BusinessObjectModel.Property p : c.bo(bo).properties) {
            TwxObject t = p.classRef.isEmpty() ? null : c.twx.find(p.classRef); String type = c.typeName(p.classRef);
            Map<String, Object> row = Json.obj("name", p.name, "type", type + (p.isList ? "[]" : ""), "list", p.isList, "required", p.required, "default", p.defaultValue, "documentation", p.documentation, "system", t == null && !type.isEmpty() && !type.equals(bare(p.classRef)));
            if (t != null) { row.put("classId", t.id); row.put("package", t.pkg.acronym); if (stack.contains(t.id)) row.put("circular", true); else if (depth >= 6) row.put("truncated", true); else row.put("properties", schema(c, t, stack, depth + 1)); }
            else if (!p.classRef.isEmpty() && (type.isEmpty() || type.equals(bare(p.classRef)))) row.put("unresolved", true);
            l.add(row);
        }
        stack.pop(); return l;
    }
    static String bare(String classRef) { return classRef.contains("/") ? classRef.substring(classRef.lastIndexOf('/') + 1) : classRef; }

    /** Diagram geometry of a service or process; other object types get an empty diagram. */
    public static Map<String, Object> diagram(RuleContext c, TwxObject o) {
        if (o.type.equals("bpd")) return DiagramBuilder.bpd(c, o);
        if (o.type.equals("process")) return DiagramBuilder.service(c, o);
        return Json.obj("type", o.type, "id", o.id, "name", o.name, "nodes", new ArrayList<Object>(), "edges", new ArrayList<Object>());
    }

    static Map<String, Object> ref(TwxObject t) { return Json.obj("id", t.id, "name", t.name, "type", t.type, "typeLabel", t.typeLabel(), "package", t.pkg.acronym); }
}
