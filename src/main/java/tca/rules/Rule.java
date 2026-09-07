package tca.rules;

import java.util.*;

/** A static-analysis rule: metadata (id, title, category, severity, description, remediation, reference) and the check itself. */
public abstract class Rule {
    public final String id, title, category, description, remediation, reference;
    public final Severity severity;
    public int impact = 1;    // 1..3 multiplier on the severity weight (business impact)
    public String confidence = "high";   // high = the finding is certain from the export; medium = needs a review in context (heuristic match)
    protected Rule(String id, String title, String category, Severity severity, String description, String remediation, String reference) { this.id = id; this.title = title; this.category = category; this.severity = severity; this.description = description; this.remediation = remediation; this.reference = reference == null ? "" : reference; }
    public Rule impact(int i) { impact = i; return this; }
    public Rule review() { confidence = "medium"; return this; }
    /** Produce findings for the whole model (rules iterate what they need through the context). */
    public abstract void check(RuleContext ctx, List<Finding> out);
    public Map<String, Object> toJson() { return tca.util.Json.obj("id", id, "title", title, "category", category, "severity", severity.name(), "description", description, "remediation", remediation, "reference", reference, "confidence", confidence); }
}
