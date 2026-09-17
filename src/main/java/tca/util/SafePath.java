package tca.util;

import java.io.File;
import java.nio.file.Path;

/** File names built from request values (report ids, workspace tokens, account and team ids) resolve through here: one path component, kept inside its base directory. */
public final class SafePath {
    private SafePath() {}
    /** {@code base/name} where {@code name} must be a single path component (no separators, no {@code ..}); the resolved path must stay directly under the base. */
    public static File child(File base, String name) {
        if (name == null || name.isEmpty() || name.length() > 200 || name.equals(".") || name.contains("..") || name.contains("/") || name.contains("\\")) throw new IllegalArgumentException("bad name");   // 200: below every file system's component limit
        Path b = base.toPath().toAbsolutePath().normalize(), p = b.resolve(name).normalize();
        if (!p.startsWith(b) || !b.equals(p.getParent())) throw new IllegalArgumentException("bad name");
        return p.toFile();
    }
}
