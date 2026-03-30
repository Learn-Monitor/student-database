package de.igslandstuhl.database.server.resources;

import java.io.InputStream;
import java.util.Collection;
import java.util.regex.Pattern;

public interface ResourceProvider {
    InputStream open(ResourceLocation location);

    Collection<ResourceLocation> list(Pattern pattern);
}
