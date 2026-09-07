package tca.parse;

import java.util.*;
import java.util.regex.*;
import tca.model.*;
import tca.util.Xml;

/** Parses a BPD object (id 25.*): lanes with flow objects, flows, parameters. */
public final class BpdParser {
    private BpdParser() {}
    private static final Pattern LOC = Pattern.compile("<location x=\"(-?\\d+)\" y=\"(-?\\d+)\"");

    static boolean hasVariable(BpdModel b, String name) { for (ServiceModel.Variable v : b.parameters) if (v.name.equals(name)) return true; for (ServiceModel.Variable v : b.variables) if (v.name.equals(name)) return true; return false; }
    public static BpdModel parse(TwxObject o) {
        BpdModel b = new BpdModel(); b.object = o; b.id = o.id; b.name = o.name; String x = o.xml;
        b.documentation = Xml.text(x, "documentation"); b.trackingEnabled = Xml.bool(x, "isTrackingEnabled"); b.criticalPathEnabled = Xml.bool(x, "isCriticalPathEnabled"); b.spcEnabled = Xml.bool(x, "isSpcEnabled");
        b.participantRef = Xml.text(x, "participantRef"); b.ownerTeamRef = Xml.text(x, "ownerTeamParticipantRef"); b.businessDataParticipantRef = Xml.text(x, "businessDataParticipantRef"); b.bpmn2 = x.contains("<bpmn2Data>") && !x.contains("<bpmn2Data isNull");
        for (String pb : Xml.blocks(x, "bpdParameter")) {
            ServiceModel.Variable v = new ServiceModel.Variable(); v.id = Xml.text(pb, "bpdParameterId"); v.name = Xml.attrOf(pb.substring(0, pb.indexOf('>')), "name"); v.classRef = Xml.text(pb, "classId"); v.isList = Xml.bool(pb, "isArrayOf");
            String pt = Xml.text(pb, "parameterType"); v.input = "1".equals(pt); v.output = "2".equals(pt); v.kind = v.input ? "input" : v.output ? "output" : "3".equals(pt) ? "private" : "parameter"; v.documentation = Xml.text(pb, "documentation"); v.hasDefault = Xml.bool(pb, "hasDefault"); v.defaultValue = Xml.text(pb, "defaultValue");
            if (v.input || v.output) b.parameters.add(v); else b.variables.add(v);
            if (Xml.bool(pb, "isProcessInstanceCorrelator") || pb.contains("<isSearchable>true")) b.searchableFields++;
        }
        for (String vb : Xml.blocks(x, "privateVariable")) {   // BPD element declarations (newer exports list private variables here, not as bpdParameter)
            String name = Xml.text(vb, "name"); if (name.isEmpty() || hasVariable(b, name)) continue;
            ServiceModel.Variable v = new ServiceModel.Variable(); v.id = Xml.attrOf(vb.substring(0, vb.indexOf('>')), "id"); v.name = name; v.classRef = Xml.text(vb, "classId"); v.isList = Xml.bool(vb, "arrayOf"); v.kind = "private"; v.hasDefault = Xml.bool(vb, "hasDefault"); v.defaultValue = Xml.text(vb, "defaultValue"); b.variables.add(v);
        }
        b.searchableFields = Math.max(b.searchableFields, count(x, "<isSearchable>true"));
        for (String lb : Xml.nestedBlocks(x, "lane")) {
            BpdModel.Lane lane = new BpdModel.Lane(); lane.id = Xml.attrOf(lb.substring(0, lb.indexOf('>')), "id"); lane.name = Xml.text(lb, "name"); lane.height = Xml.integer(lb, "height", 0); lane.system = Xml.bool(lb, "systemLane"); lane.participantRef = Xml.text(lb, "attachedParticipant");
            for (String fb : Xml.nestedBlocks(lb, "flowObject")) {
                BpdModel.FlowObject f = new BpdModel.FlowObject(); f.xml = fb; String open = fb.substring(0, fb.indexOf('>')); f.id = Xml.attrOf(open, "id"); f.componentType = Xml.attrOf(open, "componentType"); f.name = Xml.text(fb, "name"); f.laneId = lane.id; f.laneName = lane.name; f.systemLane = lane.system;
                Matcher lm = LOC.matcher(fb); if (lm.find()) { f.x = Integer.parseInt(lm.group(1)); f.y = Integer.parseInt(lm.group(2)); }
                f.documentation = Xml.text(fb, "documentation"); f.narrative = Xml.text(fb, "narrative"); f.subject = Xml.text(fb, "subject"); f.attachedRef = Xml.text(fb, "attachedActivityId"); if (f.attachedRef.isEmpty()) f.attachedRef = Xml.text(fb, "attachedProcessRef");
                f.script = Xml.text(fb, "script"); f.loopType = Xml.integer(fb, "loopType", 0); f.multiInstance = f.loopType == 2 || fb.contains("<loopType>2") || fb.contains("<isMultiInstance>true"); f.implementationType = Xml.text(fb, "implementationType"); f.taskType = Xml.text(fb, "bpmnTaskType");
                f.conditional = Xml.bool(fb, "isConditional"); f.hidden = Xml.bool(fb, "isHidden"); f.repeatable = Xml.bool(fb, "isRepeatable"); f.autoflow = Xml.bool(fb, "isAutoflowable"); f.deleteTaskOnCompletion = Xml.bool(fb, "deleteTaskOnCompletion") || fb.contains("<cleanupTask>true"); f.assignmentType = Xml.text(fb, "sendToType");
                f.eventType = Xml.text(fb, "eventType"); f.eventAction = Xml.text(fb, "eventActionType"); f.ucaRef = Xml.text(fb, "ucaId"); if (f.ucaRef.isEmpty()) f.ucaRef = Xml.text(fb, "attachedUCARef");
                f.hasErrorHandling = fb.contains("<isErrorHandlerEnabled>true") || fb.contains("errorEvent") ;
                for (String m : Xml.blocks(fb, "inputActivityParameterMapping")) { ServiceModel.Mapping mp = new ServiceModel.Mapping(); mp.input = true; mp.parameterName = Xml.text(m, "name"); mp.value = Xml.text(m, "value"); f.mappings.add(mp); }
                for (String m : Xml.blocks(fb, "outputActivityParameterMapping")) { ServiceModel.Mapping mp = new ServiceModel.Mapping(); mp.input = false; mp.parameterName = Xml.text(m, "name"); mp.value = Xml.text(m, "value"); f.mappings.add(mp); }
                f.gatewayType = Xml.text(fb, "gatewayType"); if (f.eventType.isEmpty()) f.eventType = Xml.text(fb, "eventType");
                lane.flowObjects.add(f); b.flowObjects.add(f);
                for (String ab : Xml.nestedBlocks(fb, "attachedEvent")) {   // boundary events (timer, error, message) attached to the activity
                    BpdModel.FlowObject ev = new BpdModel.FlowObject(); ev.xml = ab; String ao = ab.substring(0, ab.indexOf('>')); ev.id = Xml.attrOf(ao, "id"); ev.componentType = "Event"; ev.name = Xml.text(ab, "name"); ev.laneId = lane.id; ev.laneName = lane.name; ev.systemLane = lane.system; ev.attachedToId = f.id;
                    Matcher am = LOC.matcher(ab); if (am.find()) { ev.x = Integer.parseInt(am.group(1)); ev.y = Integer.parseInt(am.group(2)); } ev.eventType = Xml.text(ab, "eventType"); ev.eventAction = Xml.text(ab, "eventActionType"); ev.script = Xml.text(ab, "script"); ev.ucaRef = Xml.text(ab, "ucaId");
                    ev.interrupting = !"false".equals(Xml.text(ab, "cancelActivity")); lane.flowObjects.add(ev); b.flowObjects.add(ev); if (ev.isErrorEvent()) f.hasErrorHandling = true;
                }
            }
            b.lanes.add(lane);
        }
        // flows: <flow id=...> elements carry name/condition; the endpoints come from the ports of the flow objects (<outputPort><flow ref=.../>)
        Matcher fm = Pattern.compile("<flow id=\"([^\"]*)\"([^>]*)>(.*?)</flow>", Pattern.DOTALL).matcher(x);
        while (fm.find()) {
            BpdModel.Flow f = new BpdModel.Flow(); f.id = fm.group(1); f.connectionType = Xml.attrOf(fm.group(2), "connectionType"); String fb = fm.group(3);
            f.name = Xml.text(fb, "name"); f.condition = Xml.text(fb, "expression"); f.isDefault = fb.contains("<isDefaultFlow>true") || fb.contains("<isDefault>true") || fb.contains("<defaultFlow>true"); b.flows.add(f);
        }
        Map<String, BpdModel.Flow> byId = new HashMap<>(); for (BpdModel.Flow f : b.flows) byId.put(f.id, f);
        for (BpdModel.FlowObject fo : b.flowObjects) {
            for (String port : Xml.blocks(fo.xml, "outputPort")) { Matcher r = Pattern.compile("<flow ref=\"([^\"]*)\"").matcher(port); while (r.find()) { BpdModel.Flow f = byId.get(r.group(1)); if (f != null) f.fromId = fo.id; } }
            for (String port : Xml.blocks(fo.xml, "inputPort")) { Matcher r = Pattern.compile("<flow ref=\"([^\"]*)\"").matcher(port); while (r.find()) { BpdModel.Flow f = byId.get(r.group(1)); if (f != null) f.toId = fo.id; } }
        }
        for (String r : Xml.texts(x, "epvId")) b.epvRefs.add(r);
        return b;
    }
    static int count(String s, String sub) { int n = 0, i = 0; while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); } return n; }
}
