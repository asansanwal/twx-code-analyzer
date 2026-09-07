package tca.model;

/** A JavaScript snippet with its location in the TWX (used by the script rules and by the search). */
public class Script {
    public final TwxObject object;
    public final String location;   // human readable: "step 'Set SQL' (script)", "load handler", "flow condition 'Errors'"
    public final String itemId;     // step / flow object id when applicable (for diagram highlighting)
    public final String kind;       // script | condition | mapping | handler | inline | expression
    public final String code;
    public Script(TwxObject object, String location, String itemId, String kind, String code) { this.object = object; this.location = location; this.itemId = itemId == null ? "" : itemId; this.kind = kind; this.code = code == null ? "" : code; }
    public int lines() { if (code.trim().isEmpty()) return 0; int n = 1; for (int i = 0; i < code.length(); i++) if (code.charAt(i) == '\n') n++; return n; }
}
