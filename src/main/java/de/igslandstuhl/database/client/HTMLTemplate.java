package de.igslandstuhl.database.client;

import java.io.FileNotFoundException;
import java.util.Map;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.client.dynamic.DynamicFieldType;
import de.igslandstuhl.database.client.dynamic.DynamicHTMLTemplate;
import de.igslandstuhl.database.client.navigation.HTMLNavigationTemplate;
import de.igslandstuhl.database.client.navigation.NavigationAppearance;
import de.igslandstuhl.database.client.navigation.NavigationElement;
import de.igslandstuhl.database.client.navigation.NavigationType;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.resources.ResourceLocation;

public interface HTMLTemplate {
    public static final ResourceLocation meta = new ResourceLocation("meta", "templates", "templates.json");
    public String fill(Map<String, String> args);
    private static void register(HTMLTemplate template, String key) {
        Registry.templateRegistry().register(key, template);
    }
    public static void registerAll() {
        NavigationElement.registerAll();
        Map<String, ?> json = Server.getInstance().getResourceManager().readJsonResourceMerged(meta);
        json.keySet().forEach((key) -> {
            @SuppressWarnings("unchecked")
            Map<String, ?> template = (Map<String, ?>) json.get(key);

            String type = (String) template.get("type");
            switch (type) {
                case "HTMLFileTemplate":
                    try {
                        register(new HTMLFileTemplate((String) template.get("path")), key);
                    } catch (FileNotFoundException e) {
                        System.err.println("Failed to load html template " + key);
                        e.printStackTrace();
                    }
                    break;
                case "HTMLNavigationTemplate":
                    register(new HTMLNavigationTemplate(NavigationAppearance.valueOf((String) template.get("appearance")), NavigationType.valueOf((String) template.get("navigation_type"))), key);
                case "DynamicHTMLTemplate":
                    register(new DynamicHTMLTemplate(DynamicFieldType.valueOf((String) template.get("dynamic_field_type"))), key);
                default:
                    break;
            }
        });
    }
}
