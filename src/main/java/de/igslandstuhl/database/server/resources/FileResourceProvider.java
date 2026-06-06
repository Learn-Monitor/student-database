package de.igslandstuhl.database.server.resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class FileResourceProvider implements ResourceProvider {

    private final Path root;

    public FileResourceProvider(Path root) {
        this.root = root;
    }

    @Override
    public InputStream open(ResourceLocation location) {
        try {
            Path file = root
                .resolve(location.context())
                .resolve(location.namespace())
                .resolve(location.resource())
                .normalize();

            // 🔐 Security: Path Traversal verhindern
            if (!file.startsWith(root)) {
                return null;
            }

            if (!Files.exists(file) || Files.isDirectory(file)) {
                return null;
            }

            return Files.newInputStream(file);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public Collection<ResourceLocation> list(Pattern pattern) {
        List<ResourceLocation> result = new ArrayList<>();

        try {
            Files.walk(root).forEach(path -> {
                if (Files.isRegularFile(path)) {
                    Path relative = root.relativize(path);
                    String normalized = relative.toString().replace("\\", "/");

                    if (pattern.matcher(normalized).matches()) {
                        ResourceLocation loc = ResourceLocation.fromPath(normalized);
                        if (loc != null) result.add(loc);
                    }
                }
            });
        } catch (IOException e) {
            ResourceManager.LOGGER.error("Failed to get resource locations of pattern {} from file root '{}'", pattern.pattern(), root, e);
        }

        return result;
    }
}