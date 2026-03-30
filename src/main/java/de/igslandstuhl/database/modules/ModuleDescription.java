package de.igslandstuhl.database.modules;

import java.util.List;

public record ModuleDescription(String id, String name, String description, String main, List<String> depends) {
    
}
