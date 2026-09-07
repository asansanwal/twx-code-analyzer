package tca.model;

import java.util.*;

/** Parsed service (process object, id 1.*): steps (items), links, parameters, variables, layout. */
public class ServiceModel {
    public TwxObject object;
    public String id, name, processTypeCode = "", startItemId = "", errorHandlerItemId = "", documentation = "";
    public boolean errorHandlerEnabled, trackingEnabled, transactional, isRoot, mobileReady;
    public final List<Item> items = new ArrayList<>();
    public final List<Link> links = new ArrayList<>();
    public final List<Variable> parameters = new ArrayList<>();   // parameterType 1 input, 2 output, 3 private? (private variables come as processVariable)
    public final List<Variable> variables = new ArrayList<>();
    public final List<String> epvRefs = new ArrayList<>(), resourceBundleRefs = new ArrayList<>();
    public String coachLayoutXml = "";      // client-side human service coach definition(s)

    public static class Item {
        public String id, name = "", component = "", componentId = "", documentation = "", script = "", attachedRef = "", attachedName = "", errorHandlerItemId = "";
        public boolean errorHandlerEnabled, logEnabled, traceEnabled, saveExecutionContext;
        public String scriptTypeId = "";   // 2 = JavaScript, 128 = text template (<#= expr #> inserts, first token = target variable)
        public boolean isTemplate() { return "128".equals(scriptTypeId); }
        public int x, y;
        public String xml = "";
        public final List<Mapping> mappings = new ArrayList<>();
        public final List<String> preAssignments = new ArrayList<>(), postAssignments = new ArrayList<>();
        public String kind() {   // normalised step kind
            switch (component) {
                case "Script": return "script"; case "SubProcess": return "call"; case "ExitPoint": return "exit"; case "Coach": case "CoachNG": return "coach"; case "Switch": return "decision";
                case "Note": return "note"; case "StayOnPage": return "stay"; case "Join": return "join"; case "Fork": return "fork"; case "Loop": return "loop"; case "SCAConnector": case "JavaConnector": case "SKELConnector": case "WSConnector": case "WebServiceConnector": return "integration";
                case "ContentIntegration": return "content"; case "ExternalActivity": return "external"; case "TrackingPoint": return "tracking"; case "Postpone": return "postpone"; case "ECMConnector": return "content"; case "RestConnector": return "integration"; case "Timer": return "timer"; case "EventReceiver": return "event";
                default: return component.toLowerCase();
            }
        }
    }
    public static class Link { public String id, name = "", fromItemId = "", toItemId = "", endStateId = "", condition = ""; }
    public static class Mapping { public String parameterName = "", value = "", classRef = ""; public boolean input, useDefault, isList; }
    public static class Variable { public String id, name = "", classRef = "", typeName = "", defaultValue = "", documentation = ""; public boolean isList, hasDefault, input, output; public String kind = "private"; }

    public Item item(String id) { for (Item i : items) if (i.id.equals(id)) return i; return null; }
    /** Process Designer processType codes (verified on exports and the legacy analyzer's type table). */
    public String typeLabel() {
        switch (processTypeCode) {
            case "0": return "Service (deprecated, PD compatible)"; case "1": return "Decision Service"; case "2": return "Ajax Service"; case "3": return "Heritage Human Service"; case "4": return "Integration Service"; case "5": return "Deployment Service (old)";
            case "6": return "General System Service"; case "7": return "Advanced Integration Service"; case "8": return "Service (type 8)"; case "9": return "UCA Elapsed-Time Default Service"; case "10": return "Client-Side Human Service"; case "11": return "External Service";
            case "12": return "Service Flow"; case "13": return "Deployment Service Flow"; default: return "Service (type " + processTypeCode + ")";
        }
    }
    public boolean isHuman() { return processTypeCode.equals("3") || processTypeCode.equals("10"); }
    public boolean isHeritageHuman() { return processTypeCode.equals("3"); }
    public boolean isClientSide() { return processTypeCode.equals("10"); }
}
