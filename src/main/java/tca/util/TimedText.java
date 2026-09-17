package tca.util;

/**
 * A view of a string for regular expression matching with a time budget. Rule patterns and search expressions run over
 * text that comes from an uploaded export; a pathological combination of pattern and text can make java.util.regex
 * backtrack for a very long time. Every character access checks the deadline, so a match that overruns its budget
 * stops with a {@link Timeout} instead of holding a worker thread.
 */
public final class TimedText implements CharSequence {
    /** Thrown by the matcher when the budget is used up. */
    public static final class Timeout extends RuntimeException { public Timeout(String m) { super(m); } }
    private final CharSequence text; private final long deadline; private final String what; private int tick;
    public TimedText(CharSequence text, long budgetMillis, String what) { this.text = text; this.deadline = System.currentTimeMillis() + budgetMillis; this.what = what; }
    private TimedText(CharSequence text, TimedText like) { this.text = text; this.deadline = like.deadline; this.what = like.what; }
    /** A view of another text that shares this deadline (one budget for a whole search). */
    public TimedText over(CharSequence other) { return new TimedText(other, this); }
    public int length() { return text.length(); }
    public char charAt(int i) { if ((++tick & 0xfff) == 0 && System.currentTimeMillis() > deadline) throw new Timeout("regular expression matching took too long on " + what); return text.charAt(i); }
    public CharSequence subSequence(int a, int b) { return text.subSequence(a, b); }
    public String toString() { return text.toString(); }
}
