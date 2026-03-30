package de.igslandstuhl.database.client.navigation;

import java.util.Map;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.client.HTMLTemplate;

public record HTMLNavigationTemplate(NavigationAppearance appearance, NavigationType type) implements HTMLTemplate {
    @Override
    public String fill(Map<String, String> args) {
        // args are not being used by this template
        return appearance().translateToHTML(Registry.navigationRegistry().stream(type()).toList());
    }
}
