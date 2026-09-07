package tca.model;

import java.util.*;

/** Parsed process (bpd object, id 25.*): lanes, flow objects (activities, gateways, events), sequence flows, parameters. */
public class BpdModel {
    public TwxObject object;
    public String id, name, documentation = "";
    public boolean trackingEnabled, criticalPathEnabled, spcEnabled;
    public String participantRef = "", ownerTeamRef = "", businessDataParticipantRef = "";
    public final List<Lane> lanes = new ArrayList<>();
    public final List<FlowObject> flowObjects = new ArrayList<>();
    public final List<Flow> flows = new ArrayList<>();
    public final List<ServiceModel.Variable> parameters = new ArrayList<>();
    public final List<ServiceModel.Variable> variables = new ArrayList<>();
    public final List<String> epvRefs = new ArrayList<>();
    public boolean bpmn2;   // has bpmn2Data (BAW style process)
    public int searchableFields;

    public static class Lane { public String id, name = "", participantRef = ""; public boolean system; public int height; public final List<FlowObject> flowObjects = new ArrayList<>(); }
    public static class FlowObject {
        public String id, name = "", componentType = "", laneId = "", laneName = "", documentation = "", narrative = "", subject = "", attachedRef = "", attachedName = "", script = "", eventType = "", eventAction = "", ucaRef = "", implementationType = "", taskType = "", assignmentType = "";
        public boolean systemLane, multiInstance, hasErrorHandling, autoflow, deleteTaskOnCompletion, conditional, hidden, repeatable;
        public int x, y, loopType;
        public String gatewayType = "", attachedToId = "";   // attachedToId: boundary event attached to this activity
        public boolean interrupting = true;
        /** eventType codes (Process Designer): 1 start, 2 end/terminate?, 3 intermediate, 6 error ... - kept as text and also checked by name/xml */
        public boolean isErrorEvent() { return xml.contains("<eventActionType>4") || xml.contains("errorCode") || name.toLowerCase().contains("error"); }
        public boolean isTimerEvent() { return xml.contains("<eventActionType>2") || xml.contains("<timerSettings") || xml.contains("<timerType>"); }
        public boolean isMessageEvent() { return xml.contains("<eventActionType>1") || xml.contains("<ucaId>") || xml.contains("<messageEvent"); }
        public String xml = "";
        public final List<ServiceModel.Mapping> mappings = new ArrayList<>();
        public String kind() {
            String t = componentType.toLowerCase();
            if (t.contains("gateway")) return "gateway"; if (t.contains("event")) return "event"; if (t.contains("activity")) return "activity"; if (t.contains("note")) return "note"; return t;
        }
    }
    public static class Flow { public String id, name = "", fromId = "", toId = "", condition = "", connectionType = ""; public boolean isDefault; }
    public FlowObject flowObject(String id) { for (FlowObject f : flowObjects) if (f.id.equals(id)) return f; return null; }
}
