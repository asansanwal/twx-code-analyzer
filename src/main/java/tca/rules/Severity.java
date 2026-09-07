package tca.rules;

/** Finding severity; weight drives the ranking. */
public enum Severity {
    CRITICAL(100, "Critical"), MAJOR(40, "Major"), MINOR(10, "Minor"), INFO(2, "Info");
    public final int weight; public final String label;
    Severity(int w, String l) { weight = w; label = l; }
}
