package de.igslandstuhl.database.server.resources;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.igslandstuhl.database.plugins.PluginResourceProvider;
import de.igslandstuhl.database.server.Server;

/**
 * Manages Resources in the application
 */
public class ResourceManager {
    private final List<ResourceProvider> providers;
    public ResourceManager(ResourceProvider... providers) {
        this.providers = Arrays.asList(providers);
    }
    public ResourceManager() {
        this(
            new FileResourceProvider(Path.of("resources")), // highest priority
            new PluginResourceProvider(),
            new CoreResourceProvider()
        );
    }

    /**
     * For all elements of java.class.path get a Collection of resources Pattern
     * pattern = Pattern.compile(".*"); gets all resources
     *
     * @param pattern the pattern to match
     * @return the resources in the order they are found
     */
    public Collection<ResourceLocation> getResources(final Pattern pattern) {
        List<ResourceLocation> result = new ArrayList<>();

        for (ResourceProvider provider : providers) {
            result.addAll(provider.list(pattern));
        }

        return result;
    }

    /**
     * Opens resources that match the given pattern as BufferedReader.
     * Tries to open file if exists on filesystem, otherwise opens as classpath resource.
     *
     * @param pattern the pattern to match
     * @return an array of BufferedReaders for the matching resources
     */
    public BufferedReader[] openResourcesAsReader(Pattern pattern) {
        List<BufferedReader> readers = new ArrayList<>();
        for (ResourceLocation resource : getResources(pattern)) {
            try {
                readers.add(new BufferedReader(new InputStreamReader(openResourceAsStream(resource), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return readers.toArray(new BufferedReader[0]);
    }

    /**
     * Opens a resource as an InputStream.
     * The resource is identified by its location, which includes context, namespace, and resource name.
     *
     * @param location the ResourceLocation object representing the resource
     * @return an InputStream for the resource
     * @throws FileNotFoundException if the resource is not found
     */
    public InputStream openResourceAsStream(ResourceLocation location) throws FileNotFoundException {
        for (ResourceProvider provider : providers) {
            InputStream stream = provider.open(location);
            if (stream != null) {
                return stream;
            }
        }
        throw new FileNotFoundException(location.toString());
    }

    /**
     * Reads the content of a resource completely as a String.
     * The resource is identified by its location, which includes context, namespace, and resource name.
     *
     * @param location the ResourceLocation object representing the resource
     * @return the content of the resource as a String
     * @throws FileNotFoundException if the resource is not found
     */
    public String readResourceCompletely(ResourceLocation location) throws FileNotFoundException {
        return readResourceCompletely(new BufferedReader(new InputStreamReader(openResourceAsStream(location), StandardCharsets.UTF_8)));
    }

    /**
     * Reads the content of a BufferedReader completely as a String.
     * This method reads all lines from the BufferedReader and concatenates them into a single String.
     *
     * @param in the BufferedReader to read from
     * @return the content of the BufferedReader as a String
     */
    public String readResourceCompletely(BufferedReader in) {
        StringBuilder builder = new StringBuilder();
        in.lines().forEach((s) -> {
            builder.append(s);
            builder.append("\n");
        });
        return builder.toString();
    }

    /**
     * Reads a resource until an empty line is encountered.
     * This method reads lines from the BufferedReader until it encounters an empty line,
     * and returns the content read so far as a String.
     *
     * @param in the BufferedReader to read from
     * @return the content read until an empty line is encountered
     * @throws IOException if an I/O error occurs
     */
    public String readResourceTillEmptyLine(BufferedReader in) throws IOException {
        StringBuilder builder = new StringBuilder();
        Stream<String> lines = in.lines();
        for (String line : new Iterable<String>() {
            public Iterator<String> iterator() {
                return lines.iterator();
            }
        }) {
            builder.append(line);
            builder.append("\n");
            if (line == null || line.equals("")) {
                return builder.toString();
            }
        }
        return builder.toString();
    }

    /**
     * Reads a virtual resource based on the user's context and location.
     * If the resource is not virtual or does not match the expected namespace, it returns null.
     *
     * @param user the username of the user requesting the resource
     * @param location the ResourceLocation object representing the virtual resource
     * @return the content of the virtual resource as a String, or null if not applicable
     */
    public String readVirtualResource(String user, ResourceLocation location) {
        if (!location.isVirtual()) {
            return null;
        } else if (location.namespace().equals("sql")) {
            return Server.getInstance().getSQLResource(user, location.resource());
        } else {
            return null;
        }
    }

    public Map<String,?> readJsonResourceAsMap(ResourceLocation location) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(openResourceAsStream(location), StandardCharsets.UTF_8))) {
            Gson gson = new Gson();
            java.lang.reflect.Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> json = gson.fromJson(in, mapType);
            return json;
        }
    }
}