package de.igslandstuhl.database.client.dynamic;

import java.io.IOException;
import java.util.Map;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.client.HTMLTemplate;
import de.igslandstuhl.database.client.TemplatingPreprocessor;

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
                    System.err.println("Failed filling template " + arg0);
                    e.printStackTrace();
                    return "";
                }
            })
            .reduce("", (s1, s2) -> s1 + "\n" + s2);
    }
}