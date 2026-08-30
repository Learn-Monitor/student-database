package de.igslandstuhl.database.server.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class MergeHelperTest {

    private static ResourceProvider provider(String json) {
        return new ResourceProvider() {
            @Override
            public InputStream open(ResourceLocation location) {
                return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }
            @Override
            public Collection<ResourceLocation> list(Pattern pattern) {
                return List.of();
            }
        };
    }

    @Test
    public void fullMergeMergesNestedMapsAndLists() {
        ResourceProvider base = provider(
                "{ \"a\": \"old\", \"nested\": { \"x\": \"1\", \"y\": \"2\" }, \"list\": [\"a\", \"b\"] }");
        ResourceProvider override = provider(
                "{ \"a\": \"new\", \"nested\": { \"y\": \"20\", \"z\": \"30\" }, \"list\": [\"c\"] }");

        ResourceManager manager = new ResourceManager(base, override);
        Map<String, ?> result = manager.readJsonResourceFullMerged(
                new ResourceLocation("test", "main", "config"));

        assertEquals("new", result.get("a"));

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) result.get("nested");
        assertEquals("1", nested.get("x"));
        assertEquals("20", nested.get("y"));
        assertEquals("30", nested.get("z"));

        assertEquals(List.of("a", "b", "c"), result.get("list"));
    }
}
