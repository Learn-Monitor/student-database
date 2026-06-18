package de.igslandstuhl.database.plugins;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.igslandstuhl.database.server.resources.CoreResourceProvider;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.resources.ResourceManager;
import de.igslandstuhl.database.server.resources.ResourceProvider;

public class PluginResourceProvider implements ResourceProvider {

    @Override
    public InputStream open(ResourceLocation location) {
        String path = location.context() + "/" + location.namespace() + "/" + location.resource();

        for (PreLoadedPlugin module : PluginLoader.getInstance().getPluginInfos()) {
            ClassLoader cl = module.resourceLoader();
            if (cl == null) continue; // built-in plugin
            InputStream stream = cl.getResourceAsStream(path);

            if (stream != null) {
                return stream;
            }
        }

        return null;
    }

    @Override
    public List<InputStream> openAll(ResourceLocation location) {
        String path = location.context() + "/" + location.namespace() + "/" + location.resource();
        List<InputStream> inputStreams = new ArrayList<>();

        for (PreLoadedPlugin module : PluginLoader.getInstance().getPluginInfos()) {
            ClassLoader cl = module.resourceLoader();
            if (cl == null) continue; // built-in plugin
            InputStream stream = cl.getResourceAsStream(path);

            if (stream != null) {
                inputStreams.add(stream);
            }
        }

        return inputStreams;
    }
    
    @Override
    public Collection<ResourceLocation> list(Pattern pattern) {
        List<ResourceLocation> result = new ArrayList<>();
        // Virtual root – no real filesystem access needed
        final Path virtualRoot = Paths.get("").toAbsolutePath().normalize();

        for (PreLoadedPlugin plugin : PluginLoader.getInstance().getPluginInfos()) {
            try (ZipFile zip = new ZipFile(new File(plugin.resourceLoader().getURLs()[0].toURI()))) {

                Enumeration<? extends ZipEntry> entries = zip.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();

                    if (entry.isDirectory()) continue;

                    String name = entry.getName();

                    if (!CoreResourceProvider.isSafeZipEntryName(name, virtualRoot)) {
                        continue;
                    }

                    if (pattern.matcher(name).matches()) {
                        ResourceLocation loc = ResourceLocation.fromPath(name);
                        if (loc != null) result.add(loc);
                    }
                }

            } catch (Exception e) {
                ResourceManager.LOGGER.error("Failed to get resource locations of pattern {} from plugin '{}'", pattern.pattern(), plugin.description().id());
            }
        }

        return result;
    }
}
