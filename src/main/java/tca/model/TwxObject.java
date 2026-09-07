package tca.model;

import java.util.*;
import java.util.regex.*;

/** One object of a TWX package (service, BPD, coach view, business object, asset ...) with its raw XML. */
public class TwxObject {
    public static final Pattern PREFIXED_ID = Pattern.compile("\\b\\d{1,3}\\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    public final String id, type, versionId, xml;
    public String name;   // embedded subprocess BPDs are relabelled by the loader
    public final TwxPackage pkg;
    private Set<String> refs;
    public TwxObject(String id, String name, String type, String versionId, String xml, TwxPackage pkg) { this.id = id; this.name = name; this.type = type; this.versionId = versionId; this.xml = xml == null ? "" : xml; this.pkg = pkg; }
    /** Prefixed ids of other objects referenced from the XML. */
    public Set<String> refs() {
        if (refs == null) { Set<String> s = new TreeSet<>(); Matcher m = PREFIXED_ID.matcher(xml); while (m.find()) { String r = m.group(); if (!r.equals(id)) s.add(r); } refs = s; }
        return refs;
    }
    public String path() { return pkg.label() + " / " + typeLabel() + " / " + name; }
    public String typeLabel() { return typeLabel(type); }
    public static String typeLabel(String type) {
        switch (type) {
            case "process": return "Service"; case "bpd": return "Process"; case "coachView": return "Coach View"; case "twClass": return "Business Object"; case "managedAsset": return "Managed Asset";
            case "epv": return "Exposed Process Value"; case "participant": return "Team"; case "underCoverAgent": return "Undercover Agent"; case "webService": return "Web Service"; case "trackingGroup": return "Tracking Group";
            case "userAttributeDefinition": return "User Attribute"; case "environmentVariableSet": return "Environment Variables"; case "projectDefaults": return "Project Defaults"; case "SmartFolder": return "Saved Search";
            case "sla": return "SLA"; case "contribution": return "Contribution"; case "artifact": return "Artifact"; case "resourceBundle": return "Resource Bundle"; case "externalService": return "External Service"; case "timingInterval": return "Timing Interval";
            case "theme": return "Theme"; case "layout": return "Layout"; case "report": return "Report"; case "scoreboard": return "Scoreboard"; case "kpi": return "KPI"; case "eventSubscription": return "Event Subscription";
            default: return type;
        }
    }
    @Override public String toString() { return type + " " + name + " (" + id + ")"; }
}
