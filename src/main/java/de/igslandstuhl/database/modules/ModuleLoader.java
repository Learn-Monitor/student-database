package de.igslandstuhl.database.modules;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.modules.config.BoolSetting;

public class ModuleLoader {
    private final List<PreLoadedModule> moduleInfos = new ArrayList<>();
    private static final ModuleLoader INSTANCE = new ModuleLoader();
    public static ModuleLoader getInstance() {
        return INSTANCE;
    }
    private ModuleLoader() {}

    private Map<String, Object> loadYaml(URLClassLoader classLoader) {
        try (InputStream is = classLoader.getResourceAsStream("module.yml")) {
            if (is == null) return null;

            Yaml yaml = new Yaml();
            return yaml.load(is);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public PreLoadedModule loadModuleFromJar(File jarFile) {
        URLClassLoader classLoader;
        try {
            classLoader = new URLClassLoader(
                new URL[]{jarFile.toURI().toURL()},
                getClass().getClassLoader()
            );
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
        try {

            Map<String, Object> yaml = loadYaml(classLoader);
            if (yaml == null) {
                System.err.println("No module.yml found in " + jarFile.getName());
                classLoader.close();
                return null;
            }

            String mainClassName = (String) yaml.get("main");
            String id = (String) yaml.get("id");
            String name = (String) yaml.get("name");
            String description = (String) yaml.getOrDefault("description", "");
            @SuppressWarnings("unchecked")
            List<String> depends = (List<String>) yaml.getOrDefault("depends", new ArrayList<>());

            Class<?> clazz = classLoader.loadClass(mainClassName);

            if (!WebModule.class.isAssignableFrom(clazz)) {
                throw new IllegalStateException("Main class does not extend WebModule");
            }

            return new PreLoadedModule(new ModuleDescription(id, name, description, mainClassName, depends), clazz, classLoader);

        } catch (Exception e) {
            e.printStackTrace();
            try {
                classLoader.close();
            } catch (IOException e1) {
                System.out.println("FAILED to close class loader");
                e1.printStackTrace();
            }
            return null;
        }
    }
    public void load(PreLoadedModule preload) {
        WebModule module;
        try {
            module = (WebModule) preload.clazz().getDeclaredConstructor().newInstance();
            module.init(preload.description());
            module.load();
            registerModule(module);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void loadAllModules(File folder) {
        File[] jars = folder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null) return;

        List<PreLoadedModule> modules = new ArrayList<>();

        for (File jar : jars) {
            PreLoadedModule m = loadModuleFromJar(jar);
            if (m != null) {
                modules.add(m);
            }
        }

        // Check for duplicate ids
        Set<String> ids = new HashSet<>();
        for (PreLoadedModule m : modules) {
            if (!ids.add(m.description().id())) {
                throw new IllegalStateException("Duplicate module id: " + m.description().id());
            }
        }

        ModuleSort.sortModules(modules).forEach((p) -> moduleInfos.add(p));
        moduleInfos.forEach(this::load);
    }
    public void unloadModules() {
        Collections.reverse(moduleInfos);
        moduleInfos.forEach((m) -> {
            WebModule module = Registry.moduleRegistry().get(m.description().id());
            if (module != null && module.isEnabled()) {
                module.disable();
            }

            try {
                m.classLoader().close();
            } catch (IOException e) {
                throw new RuntimeException("Problem while unloading", e);
            }
        });
        moduleInfos.clear();
    }

    

    private void registerModule(WebModule module) {
        if (Registry.moduleRegistry().keyStream().anyMatch(module.getId()::equals)) {
            throw new IllegalStateException("Duplicate module id: " + module.getId());
        }
        Registry.moduleRegistry().register(module.getId(), module);
    }
    public void registerModules() {
        registerModule(new WebModule.DummyModule("result_view", "Student Results View", "The view displaying the student's current progress and prognoses for the final result", List.of(
            new BoolSetting("show_prognosis", "Show Prognosis", "Whether to display the prognosis for the final result", true),
            new BoolSetting("show_current_progress", "Show Current", "Whether to display the current progress to the subject (in percent)", true),
            new BoolSetting("show_current_grade", "Show Currently Achieved Grade", "Whether to display the grade the student would achieve when they decide to immediately stop working", false)
        )));
        loadAllModules(new File("modules"));
    }
}
