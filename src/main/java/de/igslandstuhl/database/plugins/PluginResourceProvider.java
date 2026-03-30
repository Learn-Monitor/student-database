package de.igslandstuhl.database.plugins;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.resources.ResourceProvider;

public class PluginResourceProvider implements ResourceProvider {

    @Override
    public InputStream open(ResourceLocation location) {
        String path = location.context() + "/" + location.namespace() + "/" + location.resource();

        for (PreLoadedPlugin module : PluginLoader.getInstance().getPluginInfos()) {
            ClassLoader cl = module.classLoader();
            InputStream stream = cl.getResourceAsStream(path);

            if (stream != null) {
                return stream;
            }
        }

        return null;
    }
    
    @Override
    public Collection<ResourceLocation> list(Pattern pattern) {
        List<ResourceLocation> result = new ArrayList<>();

        for (PreLoadedPlugin module : PluginLoader.getInstance().getPluginInfos()) {
            try (ZipFile zip = new ZipFile(new File(module.classLoader().getURLs()[0].toURI()))) {

                Enumeration<? extends ZipEntry> entries = zip.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();

                    if (entry.isDirectory()) continue;

                    String name = entry.getName();

                    if (pattern.matcher(name).matches()) {
                        ResourceLocation loc = ResourceLocation.fromPath(name);
                        if (loc != null) result.add(loc);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return result;
    }
}
