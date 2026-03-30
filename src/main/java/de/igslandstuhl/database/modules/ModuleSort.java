package de.igslandstuhl.database.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleSort {
    private ModuleSort() {}
    private static void visit(
        PreLoadedModule module,
        Map<String, PreLoadedModule> map,
        List<PreLoadedModule> sorted,
        Set<String> visited,
        Set<String> visiting
    ) {
        String id = module.description().id();

        if (visited.contains(id)) return;

        if (visiting.contains(id)) {
            throw new IllegalStateException("Circular dependency detected: " + id);
        }

        visiting.add(id);

        for (String dep : module.description().depends()) {
            PreLoadedModule dependency = map.get(dep);
            if (dependency == null) {
                throw new IllegalStateException("Missing dependency: " + dep + " for " + id);
            }
            visit(dependency, map, sorted, visited, visiting);
        }

        visiting.remove(id);
        visited.add(id);
        sorted.add(module);
    }
    public static List<PreLoadedModule> sortModules(List<PreLoadedModule> modules) {
        Map<String, PreLoadedModule> map = new HashMap<>();
        for (PreLoadedModule m : modules) {
            map.put(m.description().id(), m);
        }

        List<PreLoadedModule> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (PreLoadedModule m : modules) {
            visit(m, map, sorted, visited, visiting);
        }

        return sorted;
    }
}
