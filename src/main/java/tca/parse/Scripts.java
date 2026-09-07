package tca.parse;

import java.util.*;
import tca.model.*;
import tca.util.Xml;

/** Collects every JavaScript snippet of an object with its location (for the script rules and the search). */
public final class Scripts {
    private Scripts() {}
    public static List<Script> of(TwxObject o, ServiceModel svc, BpdModel bpd, CoachViewModel cv) {
        List<Script> out = new ArrayList<>();
        if (svc != null) {
            for (ServiceModel.Item it : svc.items) {
                if (!it.script.trim().isEmpty()) out.add(new Script(o, "step '" + it.name + "' (" + it.component + ")", it.id, it.isTemplate() ? "template" : "script", it.script));
                for (ServiceModel.Mapping m : it.mappings) if (looksLikeCode(m.value)) out.add(new Script(o, "step '" + it.name + "' mapping " + m.parameterName, it.id, "mapping", m.value));
                for (String a : it.preAssignments) out.add(new Script(o, "step '" + it.name + "' pre-assignment", it.id, "assignment", a));
                for (String a : it.postAssignments) out.add(new Script(o, "step '" + it.name + "' post-assignment", it.id, "assignment", a));
            }
            for (ServiceModel.Link l : svc.links) if (!l.condition.trim().isEmpty()) out.add(new Script(o, "link '" + l.name + "' condition", l.fromItemId, "condition", l.condition));
            for (ServiceModel.Variable v : svc.variables) if (v.hasDefault && looksLikeCode(v.defaultValue)) out.add(new Script(o, "variable '" + v.name + "' default value", "", "expression", v.defaultValue));
            for (ServiceModel.Variable v : svc.parameters) if (v.hasDefault && looksLikeCode(v.defaultValue)) out.add(new Script(o, "parameter '" + v.name + "' default value", "", "expression", v.defaultValue));
            if (!svc.coachLayoutXml.isEmpty()) {
                for (String b : Xml.blocks(svc.coachLayoutXml, "ns18:configData")) { String v = Xml.text(b, "ns18:value"); if (v.startsWith("tw.") || v.contains("tw.local") ) out.add(new Script(o, "coach option " + Xml.text(b, "ns18:optionName"), "", "expression", v)); }
            }
        }
        if (bpd != null) {
            for (BpdModel.FlowObject f : bpd.flowObjects) { if (!f.script.trim().isEmpty()) out.add(new Script(o, "activity '" + f.name + "' script", f.id, "script", f.script)); for (ServiceModel.Mapping m : f.mappings) if (looksLikeCode(m.value)) out.add(new Script(o, "activity '" + f.name + "' mapping " + m.parameterName, f.id, "mapping", m.value)); }
            for (BpdModel.Flow fl : bpd.flows) if (!fl.condition.trim().isEmpty()) out.add(new Script(o, "flow '" + fl.name + "' condition", fl.fromId, "condition", fl.condition));
            for (ServiceModel.Variable v : bpd.variables) if (v.hasDefault && looksLikeCode(v.defaultValue)) out.add(new Script(o, "variable '" + v.name + "' default value", "", "expression", v.defaultValue));
        }
        if (cv != null) for (CoachViewModel.Script s : cv.scripts) if (!"css".equals(s.kind) && !"html".equals(s.kind)) out.add(new Script(o, ("inline".equals(s.kind) ? "inline script '" : "handler '") + s.name + "'", "", s.kind, s.code));
        return out;
    }
    /** Mapping / default values that are more than a plain variable reference or literal. */
    static boolean looksLikeCode(String v) { if (v == null) return false; String t = v.trim(); return t.length() > 0 && (t.contains("(") || t.contains("+") || t.contains("?") || t.contains("new ") || t.contains(";") || t.contains("[") || t.contains("&&") || t.contains("||")); }
}
