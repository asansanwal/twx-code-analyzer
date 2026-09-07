package tca.parse;

import java.util.*;
import java.util.regex.*;
import tca.model.*;
import tca.util.Xml;

/** Parses a service object (process, id 1.*) into a ServiceModel. */
public final class ServiceParser {
    private ServiceParser() {}
    private static final Pattern LAYOUT = Pattern.compile("<layoutData x=\"(-?\\d+)\" y=\"(-?\\d+)\"");

    public static ServiceModel parse(TwxObject o) {
        ServiceModel s = new ServiceModel(); s.object = o; s.id = o.id; s.name = o.name; String x = o.xml;
        int head = x.indexOf("<processParameter"); if (head < 0) head = x.indexOf("<item>"); if (head < 0) head = Math.min(x.length(), 6000);
        String h = x.substring(0, head);
        s.processTypeCode = Xml.text(h, "processType"); s.startItemId = Xml.text(h, "startingProcessItemId"); s.errorHandlerEnabled = Xml.bool(h, "isErrorHandlerEnabled"); s.errorHandlerItemId = Xml.text(h, "errorHandlerItemId");
        s.trackingEnabled = Xml.bool(h, "isTrackingEnabled"); s.transactional = Xml.bool(h, "isTransactional"); s.isRoot = Xml.bool(h, "isRootProcess"); s.mobileReady = Xml.bool(h, "mobileReady"); s.documentation = Xml.text(h, "description");
        for (String b : Xml.blocks(x, "processParameter")) {
            ServiceModel.Variable v = var(b); v.name = Xml.attrOf(b.substring(0, b.indexOf('>')), "name"); String pt = Xml.text(b, "parameterType"); v.input = "1".equals(pt); v.output = "2".equals(pt); v.kind = v.input ? "input" : v.output ? "output" : "private"; s.parameters.add(v);
        }
        for (String b : Xml.blocks(x, "processVariable")) { ServiceModel.Variable v = var(b); v.name = Xml.attrOf(b.substring(0, b.indexOf('>')), "name"); v.kind = "private"; s.variables.add(v); }
        for (String b : Xml.nestedBlocks(x, "item")) {
            ServiceModel.Item it = new ServiceModel.Item(); it.xml = b; it.id = Xml.text(b, "processItemId"); it.name = Xml.text(b, "name"); it.component = Xml.text(b, "tWComponentName"); it.componentId = Xml.text(b, "tWComponentId");
            it.documentation = Xml.text(b, "documentation"); it.errorHandlerEnabled = Xml.bool(b, "isErrorHandlerEnabled"); it.errorHandlerItemId = Xml.text(b, "errorHandlerItemId"); it.logEnabled = Xml.bool(b, "isLogEnabled"); it.traceEnabled = Xml.bool(b, "isTraceEnabled"); it.saveExecutionContext = Xml.bool(b, "saveExecutionContext");
            Matcher lm = LAYOUT.matcher(b); if (lm.find()) { it.x = Integer.parseInt(lm.group(1)); it.y = Integer.parseInt(lm.group(2)); }
            String comp = Xml.textRaw(b, "TWComponent");
            it.script = Xml.text(comp, "script"); it.scriptTypeId = Xml.text(comp, "scriptTypeId"); it.attachedRef = Xml.text(comp, "attachedProcessRef"); if (it.attachedRef.isEmpty()) it.attachedRef = Xml.text(comp, "attachedActivityId");
            for (String m : Xml.blocks(comp, "parameterMapping")) { ServiceModel.Mapping mp = new ServiceModel.Mapping(); mp.parameterName = Xml.attrOf(m.substring(0, m.indexOf('>')), "name"); mp.value = Xml.text(m, "value"); mp.classRef = Xml.text(m, "classRef"); mp.input = Xml.bool(m, "isInput"); mp.useDefault = Xml.bool(m, "useDefault"); mp.isList = Xml.bool(m, "isList"); it.mappings.add(mp); }
            for (String a : Xml.texts(b, "preAssignment")) if (!a.trim().isEmpty()) it.preAssignments.add(a);
            for (String a : Xml.texts(b, "postAssignment")) if (!a.trim().isEmpty()) it.postAssignments.add(a);
            s.items.add(it);
        }
        for (String b : Xml.blocks(x, "link")) {
            ServiceModel.Link l = new ServiceModel.Link(); l.id = Xml.text(b, "processLinkId"); l.name = Xml.attrOf(b.substring(0, b.indexOf('>')), "name"); l.fromItemId = Xml.text(b, "fromProcessItemId"); l.toItemId = Xml.text(b, "toProcessItemId"); l.endStateId = Xml.text(b, "endStateId"); l.condition = Xml.text(b, "condition"); s.links.add(l);
        }
        for (String r : Xml.texts(x, "epvId")) s.epvRefs.add(r); for (String r : Xml.texts(x, "resourceBundleId")) s.resourceBundleRefs.add(r);
        int c = x.indexOf("<coachDefinition"); if (c < 0) c = x.indexOf(":coachDefinition"); if (c >= 0) s.coachLayoutXml = x.substring(c);
        return s;
    }
    static ServiceModel.Variable var(String b) {
        ServiceModel.Variable v = new ServiceModel.Variable(); v.id = Xml.text(b, "processParameterId"); if (v.id.isEmpty()) v.id = Xml.text(b, "processVariableId");
        v.classRef = Xml.text(b, "classId"); v.isList = Xml.bool(b, "isArrayOf"); v.hasDefault = Xml.bool(b, "hasDefault"); v.defaultValue = Xml.text(b, "defaultValue"); v.documentation = Xml.text(b, "description"); if (v.documentation.isEmpty()) v.documentation = Xml.text(b, "documentation"); return v;
    }
}
