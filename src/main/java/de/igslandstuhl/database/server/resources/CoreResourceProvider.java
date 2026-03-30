package de.igslandstuhl.database.server.resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class CoreResourceProvider implements ResourceProvider {
    /**
     * Checks if a zip entry name is safe (to prevent zip slipping).
     */
    private boolean isSafeZipEntryName(String entryName, Path rootDir) {
        // Resolve entry against a fixed root and normalize
        Path resolvedPath = rootDir.resolve(entryName).normalize();

        // Entry is safe if it stays within the root directory
        return resolvedPath.startsWith(rootDir);
    }
    @Override
    public InputStream open(ResourceLocation location) {
        String path = "/" + location.context() + "/" + location.namespace() + "/" + location.resource();
        return getClass().getResourceAsStream(path);
    }

    @Override
    public Collection<ResourceLocation> list(Pattern pattern) {
        List<ResourceLocation> result = new ArrayList<>();

        String classPath = System.getProperty("java.class.path", ".");
        String[] elements = classPath.split(System.getProperty("path.separator"));

        for (String element : elements) {
            Path path = Path.of(element);

            if (Files.isDirectory(path)) {
                result.addAll(getResourcesFromDirectory(path, pattern, path));
            } else {
                result.addAll(getResourcesFromJarFile(path, pattern));
            }
        }

        return result;
    }

    /**
     * Get all resources from a jar file or a directory that match the given pattern.
     * 
     * @param jarFilePath the jar file or directory to search in
     * @param pattern the pattern to match
     * @return the resources in the order they are found
     */
    private Collection<ResourceLocation> getResourcesFromJarFile(final Path jarFilePath, final Pattern pattern) {
        final ArrayList<ResourceLocation> retval = new ArrayList<>();
        // Virtual root – no real filesystem access needed
        final Path virtualRoot = Paths.get("").toAbsolutePath().normalize();
        ZipFile zf;
        try {
            zf = new ZipFile(jarFilePath.toFile());
        } catch (final ZipException e) {
            throw new Error(e);
        } catch (final NoSuchFileException e) {
            return Collections.emptySet();
        } catch (final IOException e) {
            throw new Error(e);
        }
        final Enumeration<? extends ZipEntry> e = zf.entries();
        while (e.hasMoreElements()) {
            final ZipEntry ze = e.nextElement();
            final String fileName = ze.getName();
            if (!isSafeZipEntryName(fileName, virtualRoot)) {
                // Optionally log or throw, here we skip unsafe entries
                continue;
            }
            final boolean accept = pattern.matcher(fileName).matches();
            if (accept) {
                ResourceLocation location = ResourceLocation.fromPath(fileName);
                if (location != null) retval.add(location);
            }
        }
        try {
            zf.close();
        } catch (final IOException e1) {
            throw new Error(e1);
        }
        return retval;
    }

    /**
     * Get all resources from a directory that match the given pattern.
     * 
     * @param directory the directory to search in
     * @param pattern the pattern to match
     * @return the resources in the order they are found
     */
    private Collection<ResourceLocation> getResourcesFromDirectory(final Path directory, final Pattern pattern, final Path toplevelPath) {
        final ArrayList<ResourceLocation> retval = new ArrayList<>();
        try {
            Files.list(directory).forEach((path) -> {
                if (Files.isDirectory(path)) {
                    retval.addAll(getResourcesFromDirectory(path, pattern, toplevelPath));
                } else {
                    final Path relativePath = toplevelPath.relativize(path);
                    final boolean accept = pattern.matcher(relativePath.toString()).matches();
                    if (accept) {
                        ResourceLocation location = ResourceLocation.fromPath(relativePath);
                        if (location != null) retval.add(location);
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            return retval;
        }
        return retval;
    }
}
