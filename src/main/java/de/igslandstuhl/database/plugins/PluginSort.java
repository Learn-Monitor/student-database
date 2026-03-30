package de.igslandstuhl.database.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PluginSort {
    private PluginSort() {}
    private static void visit(
        PreLoadedPlugin plugin,
        Map<String, PreLoadedPlugin> map,
        List<PreLoadedPlugin> sorted,
        Set<String> visited,
        Set<String> visiting
    ) {
        String id = plugin.description().id();

        if (visited.contains(id)) return;

        if (visiting.contains(id)) {
            throw new IllegalStateException("Circular dependency detected: " + id);
        }

        visiting.add(id);

        for (String dep : plugin.description().depends()) {
            PreLoadedPlugin dependency = map.get(dep);
            if (dependency == null) {
                throw new IllegalStateException("Missing dependency: " + dep + " for " + id);
            }
            visit(dependency, map, sorted, visited, visiting);
        }

        visiting.remove(id);
        visited.add(id);
        sorted.add(plugin);
    }
    public static List<PreLoadedPlugin> sortPlugins(List<PreLoadedPlugin> plugins) {
        Map<String, PreLoadedPlugin> map = new HashMap<>();
        for (PreLoadedPlugin m : plugins) {
            map.put(m.description().id(), m);
        }

        List<PreLoadedPlugin> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (PreLoadedPlugin m : plugins) {
            visit(m, map, sorted, visited, visiting);
        }

        return sorted;
    }
}
