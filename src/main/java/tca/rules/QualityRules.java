package tca.rules;

import java.util.*;
import tca.model.*;

/** Cross-cutting quality rules: naming of artifacts, duplicates, EPV/resource bundle usage, teams, UCAs. */
public final class QualityRules {
    private QualityRules() {}
    public static List<Rule> rules() {
        List<Rule> l = new ArrayList<>();
        l.add(new Rule("TCA-QA-001", "Artifact name with 'copy', 'test', 'old', 'temp' or 'delete'", "Quality", Severity.MINOR, "The artifact name suggests a leftover (copy of, test, old, tmp, to delete, backup). Such artifacts confuse maintainers and get deployed to production.", "Remove obsolete artifacts or rename them to their real purpose.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.twx.app.objects.values()) if (!o.type.equals("artifact") && o.name.toLowerCase().matches(".*\\b(copy of|copy\\d*|test|old|tmp|temp|backup|bak|delete|deprecated|obsolete|do not use|dont use|todo|xxx)\\b.*")) out.add(Finding.of(this, o, "", "", "", "Suspicious artifact name '" + o.name + "'", "")); }
        });
        l.add(new Rule("TCA-QA-002", "Duplicate artifact names", "Quality", Severity.MINOR, "Two artifacts of the same type have the same name (or differ only in case/whitespace) in the application or across its toolkits. Search, where-used and conversations become ambiguous.", "Rename one of them; move shared artifacts into one toolkit.", "") {
            public void check(RuleContext c, List<Finding> out) { Map<String, List<TwxObject>> m = new HashMap<>(); for (TwxPackage p : c.packages()) for (TwxObject o : p.objects.values()) { if (o.type.equals("artifact") || o.type.equals("contribution")) continue; String k = o.type + "|" + o.name.trim().toLowerCase(); List<TwxObject> x = m.get(k); if (x == null) { x = new ArrayList<>(); m.put(k, x); } x.add(o); } for (List<TwxObject> x : m.values()) if (x.size() > 1) { StringBuilder sb = new StringBuilder(); for (TwxObject o : x) sb.append(o.pkg.acronym).append(' '); out.add(Finding.of(this, x.get(0), "", "", "", x.size() + " artifacts named '" + x.get(0).name + "' (" + x.get(0).typeLabel() + ") in " + sb.toString().trim(), "")); } }
        });
        l.add(new Rule("TCA-QA-003", "Team (participant group) with static member list", "Configuration", Severity.MINOR, "The team lists users by name instead of a group / team retrieval service. Member changes require a new snapshot.", "Bind the team to an LDAP/security group or a team retrieval service; keep the static list empty.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("participant")) if (o.xml.contains("<user ") || o.xml.contains("<participantUser")) out.add(Finding.of(this, o, "", "", "", "Team '" + o.name + "' has hard-coded user members", "")); }
        });
        l.add(new Rule("TCA-QA-004", "Undercover agent without an attached service or event", "Process", Severity.MAJOR, "The UCA has no attached service (message events cannot be processed) or no schedule/event definition.", "Attach the service that handles the event and configure the schedule or the message.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("underCoverAgent")) { String ref = tca.util.Xml.text(o.xml, "attachedProcessRef"); if (ref.isEmpty() || c.twx.find(ref) == null) out.add(Finding.of(this, o, "", "", "", "Undercover agent '" + o.name + "' has no valid attached service", "")); } }
        });
        l.add(new Rule("TCA-QA-005", "Exposed process value not used", "Quality", Severity.MINOR, "An EPV of the application is not referenced by any process or service.", "Delete unused EPVs.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.twx.app.byType("epv")) if (c.referencedBy(o.id).isEmpty()) out.add(Finding.of(this, o, "", "", "", "EPV '" + o.name + "' is not used", "")); }
        });
        l.add(new Rule("TCA-QA-006", "Very large service (many steps)", "Quality", Severity.MINOR, "The service has more than 40 steps. Large flows are hard to read and to test; they usually mix several responsibilities.", "Split into smaller services called from the main flow.", "") {
            public void check(RuleContext c, List<Finding> out) { int max = c.threshold("serviceSteps", 40); for (TwxObject o : c.objects("process")) { int n = c.service(o).items.size(); if (n > max) out.add(Finding.of(this, o, "", "", "", n + " steps in one service (max " + max + ")", "")); } }
        });
        l.add(new Rule("TCA-QA-007", "Steps with default names", "Quality", Severity.INFO, "Steps still carry their generated names (Untitled, Script, Server Script, Service Task ...). Diagrams and logs are unreadable.", "Name every step after what it does.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) { int n = 0; String ev = ""; for (ServiceModel.Item it : c.service(o).items) if (!it.kind().equals("exit") && !it.kind().equals("coach") && !it.kind().equals("note") && it.name.matches("(?i)untitled\\d*|script\\d*|server script\\d*|service\\d*|sub ?process\\d*|switch\\d*|decision\\d*|untitled")) { n++; if (ev.length() < 100) ev += it.name + " "; } if (n > 0) out.add(Finding.of(this, o, "", "", "steps", n + " step(s) with default names", ev.trim())); } for (TwxObject o : c.objects("bpd")) { int n = 0; for (BpdModel.FlowObject f : c.bpd(o).flowObjects) if (f.name.matches("(?i)untitled\\d*|activity\\d*|gateway\\d*|event\\d*|task\\d*")) n++; if (n > 0) out.add(Finding.of(this, o, "", "", "flow objects", n + " flow object(s) with default names", "")); } }
        });
        return l;
    }
}
