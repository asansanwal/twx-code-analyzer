package tca.rules;

import java.util.*;
import tca.model.*;

/** Coach view / coach rules: bindings, size, deprecated views, previews, resources. */
public final class CoachRules {
    private CoachRules() {}
    static final String CAT = "UI";
    public static List<Rule> rules() {
        List<Rule> l = new ArrayList<>();
        l.add(new Rule("TCA-UI-001", "Coach with too many controls", CAT, Severity.CRITICAL, "A coach contains more than 500 controls (layout items). Rendering and every boundary event become slow; the page is unusable on slower clients.", "Split the coach into several coaches / nested human services, use Deferred Sections and paging for lists.", "IDA check-coach-with-too-many-coach-view") {
            public void check(RuleContext c, List<Finding> out) { int max = c.threshold("coachControls", 500); for (TwxObject o : c.objects("process")) { ServiceModel s = c.service(o); for (ServiceModel.Item it : s.items) if (it.kind().equals("coach")) { int n = count(it.xml, "layoutItem"); if (n > max) out.add(Finding.of(this, o, it.id, it.name, "coach '" + it.name + "'", "Coach '" + it.name + "' has " + n + " controls (max " + max + ")", "")); } if (!s.coachLayoutXml.isEmpty()) { int n = count(s.coachLayoutXml, ":layoutItem "); if (n > max) out.add(Finding.of(this, o, "", "", "coach definition", n + " controls in the coach definition (max " + max + ")", "")); } } }
        });
        l.add(new Rule("TCA-UI-002", "Coach view with a lot of inline JavaScript", CAT, Severity.MAJOR, "The coach view holds more than 300 lines of inline JavaScript. Large inline scripts are not cached by the browser, cannot be unit tested and are duplicated in every coach that uses the view.", "Move the logic into a web asset (.js file) referenced as a view resource; keep the inline script for wiring only.", "") {
            public void check(RuleContext c, List<Finding> out) { int max = c.threshold("coachViewJsLines", 300); for (TwxObject o : c.objects("coachView")) { int n = 0; for (CoachViewModel.Script s : c.coachView(o).scripts) if (!s.kind.equals("css") && !s.kind.equals("html")) n += lines(s.code); if (n > max) out.add(Finding.of(this, o, "", "", "scripts", n + " lines of JavaScript in the coach view (max " + max + ")", "")); } }
        });
        l.add(new Rule("TCA-UI-003", "Coach view without a design-time preview", CAT, Severity.INFO, "The coach view has neither a preview image nor an advanced (HTML/JS) preview; it shows as a grey box in the Process Designer canvas.", "Add a palette icon and an advanced preview (HTML snippet + helper JS) so designers see the control while building coaches.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("coachView")) { CoachViewModel v = c.coachView(o); if (v.previewHtml.isEmpty() && v.previewJs.isEmpty() && !v.template && (v.scripts.size() > 0)) out.add(Finding.of(this, o, "", "", "", "No design-time preview", "")); } }
        });
        l.add(new Rule("TCA-UI-004", "Deprecated (heritage) coach controls", "Migration", Severity.MINOR, "The coach uses controls of the heritage Coaches toolkit (Coaches / SYSC: Section, Table, Text, Button ... of the old stock controls) or heritage coach views, which are deprecated in favour of the BPM UI Toolkit and removed from newer releases.", "Replace heritage controls with the BPM UI Toolkit (BPMUI) equivalents (Vertical Layout, Table, Text, Button ...).", "IDA check-coachview-using-deprecated-coach-views") {
            public void check(RuleContext c, List<Finding> out) {
                Set<String> heritage = new HashSet<>(); for (TwxPackage p : c.twx.toolkits.values()) if (p.acronym.equals("SYSC") || p.acronym.equals("SYSRC")) heritage.addAll(p.objects.keySet());
                if (heritage.isEmpty()) return;
                for (TwxObject o : c.objects(null)) { if (!o.type.equals("process") && !o.type.equals("coachView")) continue; int n = 0; for (String r : o.refs()) if (heritage.contains(r)) n++; if (n > 0) out.add(Finding.of(this, o, "", "", "", n + " reference(s) to heritage coach controls (Coaches / Responsive Coaches toolkit)", "")); }
            }
        });
        l.add(new Rule("TCA-UI-005", "Coach view option without label or documentation", "Documentation", Severity.INFO, "Configuration options without a label/description are shown with their technical name in the designer and are hard to use.", "Give every configuration option a label, a description and a group.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("coachView")) { int n = 0; for (CoachViewModel.Option op : c.coachView(o).options) if (op.label.trim().isEmpty()) n++; if (n > 0) out.add(Finding.of(this, o, "", "", "options", n + " option(s) without label", "")); } }
        });
        l.add(new Rule("TCA-UI-006", "Coach view without binding type", CAT, Severity.INFO, "The coach view declares no business data binding; it can only work through configuration options. This is fine for layout/action views but a data control without a binding cannot be reused generically.", "Declare the binding type (data) if the view displays or edits data.", "IDA check-coachcontrol-with-no-binding-value") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("coachView")) { CoachViewModel v = c.coachView(o); boolean handlers = false; for (CoachViewModel.Script s : v.scripts) if (s.code.contains("context.binding")) handlers = true; if (v.bindingType.isEmpty() && handlers) out.add(Finding.of(this, o, "", "", "", "Scripts use context.binding but no binding type is declared", "")); } }
        });
        l.add(new Rule("TCA-UI-007", "Vendor / commercial library bundled as a web asset", "Licensing", Severity.MAJOR, "A web asset name matches a commercial UI library (Webix, DevExtreme, Kendo, Sencha/ExtJS, Highcharts, ag-Grid enterprise). Licences must be verified before distribution and such libraries add large payloads to every coach.", "Verify the licence; prefer the BPM UI Toolkit controls or open-source libraries bundled once in a shared toolkit.", "") {
            public void check(RuleContext c, List<Finding> out) { for (TwxObject o : c.objects("managedAsset")) { String n = o.name.toLowerCase(); if (n.matches(".*(webix|devextreme|dx\\.all|kendo|ext-all|extjs|highcharts|highstock|ag-grid-enterprise|syncfusion|telerik|fusioncharts).*")) out.add(Finding.of(this, o, "", "", "", "Web asset '" + o.name + "' looks like a commercial library", "")); } }
        });
        l.add(new Rule("TCA-UI-008", "Duplicate library versions in web assets", "Quality", Severity.MINOR, "Several versions of the same JavaScript library are bundled (e.g. jquery-1.x and jquery-3.x, two bootstrap.min.js). Coaches load conflicting copies and behaviour depends on load order.", "Keep one version per library in one shared toolkit.", "") {
            public void check(RuleContext c, List<Finding> out) { Map<String, List<String>> libs = new TreeMap<>(); for (TwxObject o : c.objects("managedAsset")) { java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([a-z][a-z0-9]*?)[-_.]?(?:v?\\d+(?:\\.\\d+)+)?(?:\\.min)?\\.js$").matcher(o.name.toLowerCase()); if (m.find()) { List<String> x = libs.get(m.group(1)); if (x == null) { x = new ArrayList<>(); libs.put(m.group(1), x); } x.add(o.name); } } for (Map.Entry<String, List<String>> e : libs.entrySet()) if (e.getValue().size() > 1 && new HashSet<>(e.getValue()).size() > 1) out.add(Finding.of(this, null, "", "", "web assets", "Library '" + e.getKey() + "' bundled " + e.getValue().size() + " times: " + e.getValue(), "")); }
        });
        return l;
    }
    static int count(String s, String sub) { int n = 0, i = 0; while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); } return n; }
    static int lines(String code) { if (code.trim().isEmpty()) return 0; int n = 1; for (int i = 0; i < code.length(); i++) if (code.charAt(i) == '\n') n++; return n; }
}
