package org.novaos.mobile;

import java.io.File;
import java.util.Comparator;

/** Rules for Nova Dateien (Android-independent, tested). */
public final class FileNames {
    private FileNames() {}

    /** A name for a new file or folder: not empty, no path separator, not "." or "..". */
    public static boolean valid(String name) {
        if (name == null) return false;
        String n = name.trim();
        return !n.isEmpty() && n.length() <= 255 && !n.equals(".") && !n.equals("..")
                && n.indexOf('/') < 0 && n.indexOf('\0') < 0;
    }

    /** Folders first, then by name (ignoring case). */
    public static final Comparator<File> ORDER = (a, b) -> {
        if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
        return a.getName().compareToIgnoreCase(b.getName());
    };

    /** Whether "child" lies inside "root" (both canonical paths) - protects against "../". */
    public static boolean inside(String root, String child) {
        return child.equals(root) || child.startsWith(root.endsWith("/") ? root : root + "/");
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
    }
}
