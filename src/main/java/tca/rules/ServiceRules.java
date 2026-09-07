package tca.rules;

import java.util.*;
import tca.model.*;

/** Service (service flow / human service / system service) rules: wiring, error handling, loops, variables, coaches. */
public final class ServiceRules {
    private ServiceRules() {}
    static final String CAT = "Service";
    public static List<Rule> rules() {
        List<Rule> l = new ArrayList<>();
        l.add(new Rule("TCA-SVC-001", "Step not connected to the flow", CAT, Severity.MAJOR, "A step of the service has no incoming link (and is not the start step) or no outgoing link (and is not an end/exit). It is either dead code or a flow that stops without reaching an end point.",
                "Wire the step into the flow or delete it. Every path should end in an End/Exit point.", "IDA check-service-step-incorrectly-referenced") {
            public void check(RuleContext c, List<Finding> out) {
                for (TwxObject o : c.objects("process")) { ServiceModel s = c.service(o); if (s.items.isEmpty()) continue; Set<String> in = new HashSet<>(), from = new HashSet<>();
                    for (ServiceModel.Link k : s.links) { in.add(k.toItemId); from.add(k.fromItemId); }
                    for (ServiceModel.Item it : s.items) { String kind = it.kind(); if (kind.equals("note") || kind.equals("tracking")) continue;
                        boolean start = it.id.equals(s.startItemId) || kind.equals("event") || kind.equals("start"), end = kind.equals("exit") || kind.equals("stay") || kind.equals("postpone") || kind.equals("end") || kind.equals("error");
                        if (!start && !in.contains(it.id) && !it.id.equals(s.errorHandlerItemId)) out.add(Finding.of(this, o, it.id, it.name, "step '" + it.name + "'", "Step '" + it.name + "' (" + it.component + ") has no incoming link", ""));
                        else if (!end && !from.contains(it.id)) out.add(Finding.of(this, o, it.id, it.name, "step '" + it.name + "'", "Step '" + it.name + "' (" + it.component + ") has no outgoing link: the flow stops here", "")); }
                }
            }
        });
        l.add(new Rule("TCA-SVC-002", "Service without start step", CAT, Severity.CRITICAL, "The service has steps but no start item is defined, so it cannot be executed.", "Open the service diagram and connect the Start node to the first step.", "IDA check-service-not-fully-implemented") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) { ServiceModel s = c.service(o); if (!s.items.isEmpty() && s.startItemId.isEmpty()) out.add(Finding.of(this, o, "", "", "", "No start step defined", "")); } }
        });
        l.add(new Rule("TCA-SVC-003", "Empty or unimplemented step", CAT, Severity.MAJOR, "A script step has no code, or a service/sub-process call step points to nothing. The step does nothing at runtime, which usually hides an unfinished implementation.",
                "Implement the step or remove it from the flow.", "IDA check-service-item-not-implemented") {
            public void check(RuleContext c, List<Finding> out) {
                for (TwxObject o : c.objects("process")) for (ServiceModel.Item it : c.service(o).items) { if (it.kind().equals("script") && it.script.trim().isEmpty()) out.add(Finding.of(this, o, it.id, it.name, "step '" + it.name + "'", "Script step '" + it.name + "' is empty", "")); if (it.kind().equals("call") && it.attachedRef.isEmpty()) out.add(Finding.of(this, o, it.id, it.name, "step '" + it.name + "'", "Call step '" + it.name + "' has no attached service", "")); }
            }
        });
        l.add(new Rule("TCA-SVC-004", "Call to a service that is not in the export", CAT, Severity.CRITICAL, "A step calls a service whose id is not found in the application or its toolkits (deleted or in a toolkit version that is not bundled). The service fails at runtime.",
                "Re-attach the step to an existing service; verify the toolkit dependency versions.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) for (ServiceModel.Item it : c.service(o).items) if (!it.attachedRef.isEmpty() && c.twx.find(it.attachedRef) == null) out.add(Finding.of(this, o, it.id, it.name, "step '" + it.name + "'", "Step '" + it.name + "' calls a missing artifact " + it.attachedRef, "")); }
        });
        l.add(new Rule("TCA-SVC-005", "Loop without exit in the service flow", CAT, Severity.MAJOR, "The links form a cycle whose steps have no link leaving the cycle (no condition can break it). The service will loop forever or until the transaction times out.",
                "Add a decision (Switch) with an exit condition, a loop counter with a maximum, or restructure the flow.", "IDA check-service-item-contains-infinite-loop") {
            public void check(RuleContext c, List<Finding> out) {
                for (TwxObject o : c.objects("process")) { ServiceModel s = c.service(o); if (s.links.isEmpty()) continue; Map<String, List<String>> g = new HashMap<>(); for (ServiceModel.Item it : s.items) g.put(it.id, new ArrayList<String>()); for (ServiceModel.Link k : s.links) if (g.containsKey(k.fromItemId)) g.get(k.fromItemId).add(k.toItemId);
                    for (List<String> cyc : DependencyRules.cycles(g)) { Set<String> set = new HashSet<>(cyc); boolean exit = false; for (String n : cyc) for (String t : g.get(n)) if (!set.contains(t)) exit = true;
                        ServiceModel.Item first = s.item(cyc.get(0)); StringBuilder sb = new StringBuilder(); for (String n : cyc) { ServiceModel.Item it = s.item(n); if (sb.length() > 0) sb.append(" -> "); sb.append(it == null ? n : it.name); }
                        if (!exit) out.add(Finding.of(this, o, cyc.get(0), first == null ? "" : first.name, "loop", "Loop without any exit path: " + sb, "")); }
                }
            }
        });
        l.add(new Rule("TCA-SVC-006", "No error handling in a service that calls other services", CAT, Severity.MAJOR, "The service calls sub-services / integrations but neither the service nor the calling steps have an error handler. An exception surfaces as a failed task or instance with a generic message.",
                "Attach an error (catch) event to the integration/call steps or enable the service-level error handler, log the error and map it to a business-friendly outcome.", "IDA check-bpd-component-need-exception-handle") {
            public void check(RuleContext c, List<Finding> out) {
                for (TwxObject o : c.objects("process")) { ServiceModel s = c.service(o); if (s.errorHandlerEnabled || s.items.isEmpty()) continue; int calls = 0; boolean any = false; StringBuilder names = new StringBuilder();
                    for (ServiceModel.Item it : s.items) { String k = it.kind(); if (k.equals("call") || k.equals("integration") || k.equals("content") || k.equals("external")) { calls++; if (it.errorHandlerEnabled || hasErrorLink(s, it)) any = true; else if (names.length() < 200) names.append(names.length() > 0 ? ", " : "").append(it.name); } }
                    if (calls > 0 && !any) out.add(Finding.of(this, o, "", "", "", "No error handling for " + calls + " call/integration step(s): " + names, "")); }
            }
        });
        l.add(new Rule("TCA-SVC-007", "Error handler loops back into the failing step", CAT, Severity.CRITICAL, "The error path of a step leads back to the same step (retry without a counter), which repeats the failure endlessly.", "Add a retry counter and a maximum, or route the error to an end state / notification.", "IDA check-service-exception-loop-item") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) { ServiceModel s = c.service(o); for (ServiceModel.Item it : s.items) if (!it.errorHandlerItemId.isEmpty() && (it.errorHandlerItemId.equals(it.id) || reaches(s, it.errorHandlerItemId, it.id, new HashSet<String>()))) out.add(Finding.of(this, o, it.id, it.name, "step '" + it.name + "'", "Error handler of '" + it.name + "' leads back to the step without a guard", "")); } }
        });
        l.add(new Rule("TCA-SVC-008", "Too many input or output variables", CAT, Severity.MAJOR, "The service has more than 15 input or output parameters. Long parameter lists make the service hard to call and to change; they usually indicate missing business objects.",
                "Group related parameters into a business object.", "IDA check-service-with-too-many-inputvariables") {
            public void check(RuleContext c, List<Finding> out) { int max = c.threshold("serviceParams", 15); for (TwxObject o : c.objects("process")) { int in = 0, outN = 0; for (ServiceModel.Variable v : c.service(o).parameters) { if (v.input) in++; if (v.output) outN++; } if (in > max) out.add(Finding.of(this, o, "", "", "parameters", in + " input parameters (max " + max + ")", "")); if (outN > max) out.add(Finding.of(this, o, "", "", "parameters", outN + " output parameters (max " + max + ")", "")); } }
        });
        l.add(new Rule("TCA-SVC-009", "Unused variable", CAT, Severity.MAJOR, "A private variable or parameter of the service is never referenced in any script, mapping or coach binding of the service.", "Remove the variable; unused variables confuse maintainers and still cost serialisation.", "IDA check-service-unused-variables") {
            public void check(RuleContext c, List<Finding> out) {
                for (TwxObject o : c.objects("process")) { ServiceModel s = c.service(o); String body = s.object.xml; List<ServiceModel.Variable> all = new ArrayList<>(s.variables); all.addAll(s.parameters);
                    for (ServiceModel.Variable v : all) { if (v.name.isEmpty()) continue; String ref = "tw.local." + v.name; int n = count(body, ref); if (n == 0 && !(v.input || v.output) && !body.contains("\"" + v.name + "\"") ) out.add(Finding.of(this, o, "", "", "variable '" + v.name + "'", "Private variable '" + v.name + "' is never used", "")); }
                }
            }
        });
        l.add(new Rule("TCA-SVC-010", "Script on the exit point", CAT, Severity.MAJOR, "Code attached to an End/Exit point runs after the outputs are already mapped; it is easy to miss when reading the flow and cannot be error-handled.", "Move the logic into a script step before the exit.", "IDA check-service-event-end-contains-script") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) for (ServiceModel.Item it : c.service(o).items) if (it.kind().equals("exit") && !it.script.trim().isEmpty()) out.add(Finding.of(this, o, it.id, it.name, "step '" + it.name + "'", "Exit point '" + it.name + "' contains a script", "")); }
        });
        l.add(new Rule("TCA-SVC-011", "Too many coaches in one human service", CAT, Severity.MAJOR, "A human service with more than 5 coaches is hard to follow and to test; it usually contains several tasks in one service.", "Split the human service by task or use nested client-side human services / coach views for reusable screens.", "IDA check-humanservice-with-too-many-coach") {
            public void check(RuleContext c, List<Finding> out) { int max = c.threshold("coachesPerService", 5); for (TwxObject o : c.objects("process")) { int n = 0; for (ServiceModel.Item it : c.service(o).items) if (it.kind().equals("coach")) n++; if (n > max) out.add(Finding.of(this, o, "", "", "", n + " coaches in one human service (max " + max + ")", "")); } }
        });
        l.add(new Rule("TCA-SVC-012", "Stay-on-page directly after a postpone", CAT, Severity.MAJOR, "A Stay On Page event follows a Postpone step; the postponed task is immediately resumed, which defeats the postpone.", "Route the postpone to an end state, or remove the stay-on-page event.", "IDA check-service-SOPE-directly-follow-postpone-object") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) { ServiceModel s = c.service(o); for (ServiceModel.Link k : s.links) { ServiceModel.Item a = s.item(k.fromItemId), b = s.item(k.toItemId); if (a != null && b != null && a.kind().equals("postpone") && b.kind().equals("stay")) out.add(Finding.of(this, o, a.id, a.name, "step '" + a.name + "'", "Stay On Page directly after Postpone '" + a.name + "'", "")); } } }
        });
        l.add(new Rule("TCA-SVC-013", "Decision (Switch) without a default path", CAT, Severity.CRITICAL, "A switch step has only conditional outgoing links. When no condition matches the flow stops silently in the middle of the service.", "Add an unconditional (default/otherwise) link from the switch.", "IDA check-bpd-gateway-condition") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) { ServiceModel s = c.service(o); for (ServiceModel.Item it : s.items) if (it.kind().equals("decision")) { int outs = 0; boolean def = false; for (ServiceModel.Link k : s.links) if (k.fromItemId.equals(it.id)) { outs++; if (k.condition.trim().isEmpty() || k.endStateId.isEmpty() || k.name.equalsIgnoreCase("default") || k.name.equalsIgnoreCase("otherwise")) def = true; } if (outs > 0 && !def) out.add(Finding.of(this, o, it.id, it.name, "step '" + it.name + "'", "Switch '" + it.name + "' has " + outs + " conditional paths and no default path", "")); } } }
        }.impact(2));
        l.add(new Rule("TCA-SVC-014", "Step logging / tracing enabled", CAT, Severity.MINOR, "A step has execution logging or tracing switched on. In production this writes every execution to the logs / trace tables and slows the service down.", "Disable logging and tracing on the step before releasing (use it only while debugging).", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) { int log = 0, ctx = 0; String first = "", firstName = ""; for (ServiceModel.Item it : c.service(o).items) { if (it.logEnabled || it.traceEnabled) { log++; if (first.isEmpty()) { first = it.id; firstName = it.name; } } if (it.saveExecutionContext) ctx++; } if (log > 0) out.add(Finding.of(this, o, first, firstName, "steps", log + " step(s) with logging/tracing enabled", "")); if (ctx > 0) { Finding f = Finding.of(this, o, "", "", "steps", ctx + " step(s) save the execution context (debug setting)", ""); f.severity = Severity.INFO.name(); f.score = Severity.INFO.weight; out.add(f); } } }
        });
        l.add(new Rule("TCA-SVC-015", "Service without documentation", "Documentation", Severity.MINOR, "The service has no documentation text.", "Describe purpose, inputs/outputs and side effects in the service documentation field.", "IDA check-service-documentation") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) if (c.service(o).documentation.trim().isEmpty()) out.add(Finding.of(this, o, "", "", "", "No documentation", "")); }
        });
        l.add(new Rule("TCA-SVC-016", "Variable name does not follow camelCase", "Naming", Severity.MAJOR, "A variable or parameter name is not lowerCamelCase (starts with a capital, contains spaces, underscores or special characters). Consistent names make mappings and scripts readable and avoid escaping problems.",
                "Rename the variable to lowerCamelCase (e.g. customerId) and update the mappings.", "IDA check-service-variables-naming-conversion") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) { ServiceModel s = c.service(o); List<ServiceModel.Variable> all = new ArrayList<>(s.variables); all.addAll(s.parameters); for (ServiceModel.Variable v : all) if (!v.name.isEmpty() && !v.name.matches("[a-z][A-Za-z0-9]*")) out.add(Finding.of(this, o, "", "", "variable '" + v.name + "'", "Variable '" + v.name + "' is not lowerCamelCase", "")); } }
        });
        l.add(new Rule("TCA-SVC-017", "Variable without documentation", "Documentation", Severity.INFO, "A parameter has no description. Callers of the service cannot see what it expects.", "Document every input and output parameter.", "IDA check-service-variables-documentation") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) { int n = 0; for (ServiceModel.Variable v : c.service(o).parameters) if (v.documentation.trim().isEmpty()) n++; if (n > 0) out.add(Finding.of(this, o, "", "", "parameters", n + " parameter(s) without documentation", "")); } }
        });
        l.add(new Rule("TCA-SVC-018", "Too many nested client-side human services", CAT, Severity.MAJOR, "A client-side human service nests more than 25 other client-side human services (directly). Very deep UI nesting slows the coach generation and the browser.", "Flatten the UI structure with coach views instead of nested services.", "IDA check-service-with-too-many-nested-cshs") {
            public void check(RuleContext c, List<Finding> out) { int max = c.threshold("nestedCshs", 25); for (TwxObject o : c.objects("process")) { ServiceModel s = c.service(o); if (!s.isClientSide()) continue; int n = 0; for (ServiceModel.Item it : s.items) if (it.kind().equals("call")) { TwxObject t = c.twx.find(it.attachedRef); if (t != null && t.type.equals("process") && c.service(t).isClientSide()) n++; } if (n > max) out.add(Finding.of(this, o, "", "", "", n + " nested client-side human services (max " + max + ")", "")); } }
        });
        l.add(new Rule("TCA-SVC-019", "Heritage human service", "Migration", Severity.MINOR, "The service is a heritage (server-side) human service. Heritage human services are deprecated in BAW; they cannot use the modern coach features and are removed from newer releases.", "Convert the human service to a client-side human service (Process Designer conversion wizard) and re-test the coaches.", "IDA check-heritage-services-deprecated") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("process")) if (c.service(o).isHeritageHuman()) out.add(Finding.of(this, o, "", "", "", "Heritage human service '" + o.name + "'", "")); }
        });
        return l;
    }
    static Finding infoLoop(Rule r, TwxObject o, String id, ServiceModel.Item first, String path) { Finding f = Finding.of(r, o, id, first == null ? "" : first.name, "loop", "Loop in the flow (has an exit path; verify the exit condition is always reachable): " + path, ""); f.severity = Severity.INFO.name(); f.score = Severity.INFO.weight; return f; }
    static boolean hasErrorLink(ServiceModel s, ServiceModel.Item it) { for (ServiceModel.Link k : s.links) if (k.fromItemId.equals(it.id) && (k.name.toLowerCase().contains("error") || k.endStateId.toLowerCase().contains("error"))) return true; return false; }
    static boolean reaches(ServiceModel s, String from, String target, Set<String> seen) { if (from.equals(target)) return true; if (!seen.add(from)) return false; for (ServiceModel.Link k : s.links) if (k.fromItemId.equals(from) && reaches(s, k.toItemId, target, seen)) return true; return false; }
    static int count(String s, String sub) { int n = 0, i = 0; while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); } return n; }
}
