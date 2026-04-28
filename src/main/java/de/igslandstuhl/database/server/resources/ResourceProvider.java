package de.igslandstuhl.database.server.resources;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public interface ResourceProvider {
    InputStream open(ResourceLocation location);
    
    default List<InputStream> openAll(ResourceLocation location) {
        InputStream stream = open(location);
        if (stream == null) return List.of();
        else return List.of(stream);
    }

    Collection<ResourceLocation> list(Pattern pattern);
}
