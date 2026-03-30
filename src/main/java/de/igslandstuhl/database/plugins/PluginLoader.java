package de.igslandstuhl.database.plugins;

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
import de.igslandstuhl.database.plugins.config.BoolSetting;

public class PluginLoader {
    private final List<PreLoadedPlugin> pluginInfos = new ArrayList<>();
    public List<PreLoadedPlugin> getPluginInfos() {
        return pluginInfos;
    }
    private static final PluginLoader INSTANCE = new PluginLoader();
    public static PluginLoader getInstance() {
        return INSTANCE;
    }
    private PluginLoader() {}

    private Map<String, Object> loadYaml(URLClassLoader classLoader) {
        try (InputStream is = classLoader.getResourceAsStream("plugin.yml")) {
            if (is == null) return null;

            Yaml yaml = new Yaml();
            return yaml.load(is);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public PreLoadedPlugin loadPluginFromJar(File jarFile) {
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
                System.err.println("No plugin.yml found in " + jarFile.getName());
                classLoader.close();
                return null;
            }

            String mainClassName = (String) yaml.get("main");
            String id = (String) yaml.get("id");
            if (id == null || mainClassName == null) {
                throw new IllegalStateException("Invalid plugin.yml in " + jarFile.getName() + ": you must define id and main");
            }
            String name = (String) yaml.getOrDefault("name", id);
            String description = (String) yaml.getOrDefault("description", "");

            Object dependsObj = yaml.get("depends");
            List<String> depends = new ArrayList<>();

            if (dependsObj instanceof List<?>) {
                for (Object o : (List<?>) dependsObj) {
                    if (o instanceof String s) {
                        depends.add(s);
                    }
                }
            }

            Class<?> clazz = classLoader.loadClass(mainClassName);

            if (!Plugin.class.isAssignableFrom(clazz)) {
                throw new IllegalStateException("Main class does not extend Plugin");
            }

            return new PreLoadedPlugin(new PluginDescription(id, name, description, mainClassName, depends), clazz, classLoader);

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
    public void load(PreLoadedPlugin preload) {
        Plugin plugin;
        try {
            plugin = (Plugin) preload.clazz().getDeclaredConstructor().newInstance();
            plugin.init(preload.description());
            registerPlugin(plugin);
            plugin.load();
            if (plugin.getConfig() == null) {
                throw new NullPointerException("Plugin must have a config");
            }
        } catch (Exception e) {
            System.err.println("Failed to load plugin: " + preload.description().id());
            pluginInfos.remove(preload);
            if (Registry.pluginRegistry().get(preload.description().id()) != null) Registry.pluginRegistry().unregister(preload.description().id());
            e.printStackTrace();
        }
    }
    public void loadAllPlugins(File folder) {
        File[] jars = folder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null) return;

        List<PreLoadedPlugin> plugins = new ArrayList<>();

        for (File jar : jars) {
            PreLoadedPlugin m = loadPluginFromJar(jar);
            if (m != null) {
                plugins.add(m);
            }
        }

        // Check for duplicate ids
        Set<String> ids = new HashSet<>();
        for (PreLoadedPlugin p : plugins) {
            if (!ids.add(p.description().id())) {
                throw new IllegalStateException("Duplicate module id: " + p.description().id());
            }
        }

        PluginSort.sortPlugins(plugins).forEach((p) -> pluginInfos.add(p));
        pluginInfos.forEach(this::load);
    }
    public void enablePlugins() {
        pluginInfos.forEach((p) -> {
            Plugin plugin = Registry.pluginRegistry().get(p.description().id());
            if (plugin.getConfig().isEnabledOnStart()) {
                plugin.enable();
            }
        });
    }
    public void unloadPlugins() {
        Collections.reverse(pluginInfos);
        pluginInfos.forEach((p) -> {
            Plugin plugin = Registry.pluginRegistry().get(p.description().id());
            plugin.getConfig().save();
            if (plugin != null && plugin.isEnabled()) {
                plugin.disable();
            }
            Registry.pluginRegistry().unregister(plugin.getId());

            try {
                p.classLoader().close();
            } catch (IOException e) {
                throw new RuntimeException("Problem while unloading", e);
            }
        });
        pluginInfos.clear();
    }

    

    private void registerPlugin(Plugin plugin) {
        if (Registry.pluginRegistry().keyStream().anyMatch(plugin.getId()::equals)) {
            throw new IllegalStateException("Duplicate module id: " + plugin.getId());
        }
        Registry.pluginRegistry().register(plugin.getId(), plugin);
    }
    public void registerPlugins() {
        registerPlugin(new Plugin.DummyModule("result_view", "Student Results View", "The view displaying the student's current progress and prognoses for the final result", List.of(
            new BoolSetting("show_prognosis", "Show Prognosis", "Whether to display the prognosis for the final result", true),
            new BoolSetting("show_current_progress", "Show Current", "Whether to display the current progress to the subject (in percent)", true),
            new BoolSetting("show_current_grade", "Show Currently Achieved Grade", "Whether to display the grade the student would achieve when they decide to immediately stop working", false)
        )));
        loadAllPlugins(new File("plugins"));
    }
}
