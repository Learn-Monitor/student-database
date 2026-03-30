package de.igslandstuhl.database.plugins;

import java.util.List;

public record PluginDescription(String id, String name, String description, String main, List<String> depends) {
    
}
