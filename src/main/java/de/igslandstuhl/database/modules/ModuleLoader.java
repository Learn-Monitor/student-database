package de.igslandstuhl.database.modules;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.modules.config.BoolSetting;

public class ModuleLoader {
    private final List<URLClassLoader> classLoaders = new ArrayList<>();
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
    public void loadModulesFromJar(File jarFile) {
        try {
            URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarFile.toURI().toURL()},
                getClass().getClassLoader()
            );
            classLoaders.add(classLoader);

            Map<String, Object> yaml = loadYaml(classLoader);
            if (yaml == null) {
                System.err.println("No module.yml found in " + jarFile.getName());
                return;
            }

            String mainClassName = (String) yaml.get("main");
            String id = (String) yaml.get("id");
            String name = (String) yaml.get("name");
            String description = (String) yaml.get("description");

            Class<?> clazz = classLoader.loadClass(mainClassName);

            if (!WebModule.class.isAssignableFrom(clazz)) {
                throw new IllegalStateException("Main class does not extend WebModule");
            }

            WebModule module = (WebModule) clazz.getDeclaredConstructor().newInstance();
            module.init(id, name, description);
            module.load();
            registerModule(module);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void loadAllModules(File folder) {
        File[] jars = folder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null) return;

        for (File jar : jars) {
            loadModulesFromJar(jar);
        }
    }
    public void unloadModules() {
        classLoaders.forEach((l) -> {
            try {
                l.close();
            } catch (IOException e) {
                throw new RuntimeException("Problem while unloading", e);
            }
        });
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
