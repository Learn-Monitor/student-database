package de.igslandstuhl.database.client.dynamic;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.gson.reflect.TypeToken;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.client.HTMLTemplate;
import de.igslandstuhl.database.client.TemplatingPreprocessor;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.resources.ResourceLocation;

public record DynamicHTMLTemplate(DynamicFieldType type) implements HTMLTemplate {
    @Override
    public String fill(Map<String, String> args) {
        return Registry.dynamicTemplatesRegistry().stream(type)
            .map(Registry.templateRegistry()::get)
            .map((t) -> t.fill(args))
            .map(arg0 -> {
                try {
                    return TemplatingPreprocessor.getInstance().executeTemplating(arg0);
                } catch (IOException e) {
                    HTMLTemplate.LOGGER.error("Failed filling template '{}'", arg0, e);
                    return "";
                }
            })
            .reduce("", (s1, s2) -> s1 + "\n" + s2);
    }
    public static final ResourceLocation meta = new ResourceLocation("meta", "dynamic", "dynamic_elements.json");
    public static void registerDynamicElements() {
        List<Map<String,String>> elements = Server.getInstance().getResourceManager().readJsonListMerged(meta, new TypeToken<List<Map<String,String>>>() {});
        elements.forEach((m) -> {
            DynamicFieldType type = DynamicFieldType.valueOf(m.get("type"));
            String template = m.get("template");
            Registry.dynamicTemplatesRegistry().register(type, template);
        });
    }
}