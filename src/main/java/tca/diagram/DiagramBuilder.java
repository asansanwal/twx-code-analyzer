package tca.diagram;

import java.util.*;
import tca.model.*;
import tca.rules.RuleContext;
import tca.util.Json;

/** Builds a renderable diagram (nodes with coordinates, edges, lanes) for a service flow or a process (BPD) from the TWX model.
 *  The UI draws it as SVG and highlights the nodes that carry findings. */
public final class DiagramBuilder {
    private DiagramBuilder() {}

    public static Map<String, Object> service(RuleContext c, TwxObject o) {
        ServiceModel s = c.service(o); List<Object> nodes = new ArrayList<>(), edges = new ArrayList<>();
        Map<String, ServiceModel.Item> byId = new HashMap<>(); for (ServiceModel.Item it : s.items) byId.put(it.id, it);
        for (ServiceModel.Item it : s.items) {
            String label = it.name; String sub = it.component; TwxObject t = it.attachedRef.isEmpty() ? null : c.twx.find(it.attachedRef);
            nodes.add(Json.obj("id", it.id, "name", label, "kind", it.kind(), "component", it.component, "x", it.x, "y", it.y, "start", it.id.equals(s.startItemId), "errorHandler", it.id.equals(s.errorHandlerItemId),
                    "attachedRef", it.attachedRef, "attachedName", t == null ? (it.attachedRef.isEmpty() ? "" : "(missing) " + it.attachedRef) : t.name, "attachedType", t == null ? "" : t.type, "script", it.script, "scriptType", it.isTemplate() ? "template" : "javascript", "documentation", it.documentation, "mappings", mappings(it.mappings)));
        }
        for (ServiceModel.Link k : s.links) edges.add(Json.obj("id", k.id, "from", k.fromItemId, "to", k.toItemId, "name", k.name, "condition", k.condition, "endState", k.endStateId, "error", k.name.toLowerCase().contains("error")));
        for (ServiceModel.Item it : s.items) if (!it.errorHandlerItemId.isEmpty() && byId.containsKey(it.errorHandlerItemId)) edges.add(Json.obj("id", it.id + "-err", "from", it.id, "to", it.errorHandlerItemId, "name", "error", "condition", "", "endState", "", "error", true));
        List<Object> vars = new ArrayList<>(); for (ServiceModel.Variable v : s.parameters) vars.add(var(c, v)); for (ServiceModel.Variable v : s.variables) vars.add(var(c, v));
        return Json.obj("type", "service", "id", o.id, "name", o.name, "subtype", s.typeLabel(), "package", o.pkg.label(), "documentation", s.documentation, "start", s.startItemId, "nodes", nodes, "edges", edges, "variables", vars);
    }

    public static Map<String, Object> bpd(RuleContext c, TwxObject o) {
        BpdModel b = c.bpd(o); List<Object> nodes = new ArrayList<>(), edges = new ArrayList<>(), lanes = new ArrayList<>(); int y = 0;
        for (BpdModel.Lane ln : b.lanes) { TwxObject t = c.twx.find(ln.participantRef); lanes.add(Json.obj("id", ln.id, "name", ln.name, "system", ln.system, "height", ln.height, "y", y, "team", t == null ? "" : t.name)); y += ln.height > 0 ? ln.height : 200; }
        Map<String, Integer> laneY = new HashMap<>(); for (Object lo : lanes) { Map<?, ?> m = (Map<?, ?>) lo; laneY.put((String) m.get("id"), (Integer) m.get("y")); }
        for (BpdModel.FlowObject f : b.flowObjects) {
            TwxObject t = f.attachedRef.isEmpty() ? null : c.twx.find(f.attachedRef); Integer ly = laneY.get(f.laneId);
            nodes.add(Json.obj("id", f.id, "name", f.name, "kind", f.kind(), "componentType", f.componentType, "x", f.x, "y", f.y + (ly == null ? 0 : ly), "lane", f.laneName, "system", f.systemLane, "attachedRef", f.attachedRef, "attachedName", t == null ? (f.attachedRef.isEmpty() ? "" : "(missing)") : t.name, "attachedType", t == null ? "" : t.type,
                    "gatewayType", f.gatewayType, "eventType", f.eventType, "attachedTo", f.attachedToId, "error", f.isErrorEvent(), "timer", f.isTimerEvent(), "message", f.isMessageEvent(), "multiInstance", f.multiInstance, "subject", f.subject, "documentation", f.documentation, "script", f.script));
        }
        for (BpdModel.Flow fl : b.flows) edges.add(Json.obj("id", fl.id, "from", fl.fromId, "to", fl.toId, "name", fl.name, "condition", fl.condition, "isDefault", fl.isDefault));
        List<Object> vars = new ArrayList<>(); for (ServiceModel.Variable v : b.parameters) vars.add(var(c, v)); for (ServiceModel.Variable v : b.variables) vars.add(var(c, v));
        return Json.obj("type", "bpd", "id", o.id, "name", o.name, "subtype", "Process", "package", o.pkg.label(), "documentation", b.documentation, "lanes", lanes, "nodes", nodes, "edges", edges, "variables", vars);
    }

    static List<Object> mappings(List<ServiceModel.Mapping> ms) { List<Object> l = new ArrayList<>(); for (ServiceModel.Mapping m : ms) l.add(Json.obj("name", m.parameterName, "value", m.value, "input", m.input, "useDefault", m.useDefault)); return l; }
    static Map<String, Object> var(RuleContext c, ServiceModel.Variable v) { return Json.obj("name", v.name, "kind", v.kind, "type", c.typeName(v.classRef) + (v.isList ? "[]" : ""), "default", v.defaultValue, "documentation", v.documentation); }
}
