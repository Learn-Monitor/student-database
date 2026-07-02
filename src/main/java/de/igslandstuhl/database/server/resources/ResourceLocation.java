package de.igslandstuhl.database.server.resources;

import java.io.File;
import java.nio.file.Path;
import java.util.regex.Matcher;

/**
 * Represents a resource location with context, namespace, and resource name.
 * This class is used to identify resources in the application.
 */
public record ResourceLocation(String context, String namespace, String resource) {
    /**
     * Constructs a ResourceLocation with the specified context, namespace, and resource name.
     *
     * @param context   the context of the resource (e.g., "virtual", "main")
     * @param resourceID  the name of the resource (e.g., "json:config.json", "meta:data.txt")
     */
    public static ResourceLocation get(String context, String resourceID) {
        String[] parts = resourceID.split(":");
        if (parts.length > 1) {
            return new ResourceLocation(context, parts[0], parts[1]);
        } else {
            return new ResourceLocation(context, "main", resourceID);
        }
    }
    public boolean isVirtual() {
        return context.equals("virtual");
    }
    public static ResourceLocation fromPath(Path path) {
        Path relativePath;
        try {
            relativePath = Path.of(".").relativize(path);
        } catch (IllegalArgumentException e) {
            relativePath = path;
        }
        String rel = relativePath.toString();
        while (rel.startsWith(".") || rel.startsWith(File.separator)) {
            rel = rel.substring(1);
        }
        return fromRelativePath(rel);
    }
    public static ResourceLocation fromPath(String path) {
        return fromPath(Path.of(path));
    }
    public static ResourceLocation fromRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        // Normalize the path to eliminate any "." or ".." segments
        Path normalized = Path.of(relativePath).normalize();
        String normalizedStr = normalized.toString();
        // Reject paths that still contain traversal segments after normalization
        if (normalizedStr.contains(".." + File.separator) ||
            normalizedStr.contains(File.separator + "..") ||
            normalizedStr.equals("..")) {
            return null;
        }
        String[] parts = normalizedStr.split(Matcher.quoteReplacement(File.separator));
        if (parts.length != 3) {
            return null;
        }
        return new ResourceLocation(parts[0], parts[1], parts[2]);
    }
}
