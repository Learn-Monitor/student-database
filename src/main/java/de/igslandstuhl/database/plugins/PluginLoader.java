package de.igslandstuhl.database.plugins;

import java.io.File;
import java.io.FileNotFoundException;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import de.igslandstuhl.database.Registry;

public class PluginLoader {
    public static final Logger LOGGER = LoggerFactory.getLogger(PluginLoader.class);
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
            LOGGER.error("Failed loading yaml from classLoader {}", classLoader.getName());
            return null;
        }
    }
    public PreLoadedPlugin loadPluginFromJar(File jarFile) {
        URLClassLoader classLoader;
        URLClassLoader resourceLoader;
        try {
            classLoader = new URLClassLoader(
                new URL[]{jarFile.toURI().toURL()},
                getClass().getClassLoader()
            );
            resourceLoader = new URLClassLoader(
                new URL[]{jarFile.toURI().toURL()},
                null
            );
        } catch (MalformedURLException e) {
            LOGGER.error("URL of {} corrupted", jarFile.getName(), e);
            return null;
        }
        try {

            Map<String, Object> yaml = loadYaml(classLoader);
            if (yaml == null) {
                LOGGER.error("No plugin.yml found in {}", jarFile.getName());
                throw new FileNotFoundException("No plugin.yml found");
            }

            String mainClassName = (String) yaml.get("main");
            String id = (String) yaml.get("id");
            if (id == null || mainClassName == null) {
                LOGGER.error("Invalid plugin.yml in {}: you must define id and main", jarFile.getName());
                throw new IllegalArgumentException("Invalid plugin.yml");
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
                LOGGER.error("{}, the main class of {} does not extend Plugin", clazz.getCanonicalName(), jarFile.getName());
                throw new ClassCastException("Plugin main class does not extend Plugin");
            }

            return new PreLoadedPlugin(new PluginDescription(id, name, description, mainClassName, depends), clazz, classLoader, resourceLoader);

        } catch (Exception e) {
            LOGGER.error("Failed to preload plugin {}", jarFile.getName(), e);
            try {
                classLoader.close();
            } catch (IOException e1) {
                LOGGER.error("Failed to close classloader for incomplete plugin {}", jarFile.getName(), e1);
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
                LOGGER.error("Plugin '{}' does not have a config", preload.description().id());
                throw new NullPointerException("Plugin must have a config");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load plugin '{}'", preload.description().id(), e);
            pluginInfos.remove(preload);
            if (Registry.pluginRegistry().get(preload.description().id()) != null) Registry.pluginRegistry().unregister(preload.description().id());
        }
    }
    public void preloadAllPlugins(File folder) {
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
                LOGGER.error("Duplicate plugin id '{}', aborting", p.description().id());
                throw new IllegalStateException("Duplicate plugin id");
            }
        }

        PluginSort.sortPlugins(plugins).forEach((p) -> pluginInfos.add(p));
    }
    public void preloadBuiltinPlugins() {
        LOGGER.info("Preloading built-in plugins...");
        Registry.builtinPluginRegistry().keyStream().forEach((id) -> {
            Class<? extends BuiltinPlugin> clazz = Registry.builtinPluginRegistry().get(id);
            try {
                PluginDescription description = clazz.getDeclaredConstructor().newInstance().getDescriptionAnnotation();
                pluginInfos.add(new PreLoadedPlugin(description, clazz, null, null));
            } catch (Exception e) {
                LOGGER.error("Failed to preload built-in plugin '{}'", id, e);
            }
        });
    }
    public void loadAllPreloadedPlugins() {
        pluginInfos.forEach(this::load);
    }
    public void enablePlugins() {
        LOGGER.info("Enabling plugins...");
        pluginInfos.forEach((p) -> {
            Plugin plugin = Registry.pluginRegistry().get(p.description().id());
            if (plugin.getConfig().isEnabledOnStart()) {
                plugin.enable();
            }
        });
    }
    public void unloadPlugins() {
        LOGGER.info("Unloading plugins...");
        Collections.reverse(pluginInfos);
        pluginInfos.forEach((p) -> {
            Plugin plugin = Registry.pluginRegistry().get(p.description().id());
            plugin.getConfig().save();
            if (plugin != null && plugin.isEnabled()) {
                plugin.disable();
            }
            Registry.pluginRegistry().unregister(plugin.getId());

            try {
                if (p.classLoader() != null)
                    p.classLoader().close();
                if (p.resourceLoader() != null)
                    p.resourceLoader().close();
            } catch (IOException e) {
                LOGGER.error("Failed to unload plugin '{}'", plugin.getId());
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
    public void preloadPlugins() {
        LOGGER.info("Preloading plugins from directory \"plugins\"...");
        preloadBuiltinPlugins();
        preloadAllPlugins(new File("plugins"));
    }
    public void registerPlugins() {
        LOGGER.info("Registering plugins from directory \"plugins\"...");
        loadAllPreloadedPlugins();
    }
}
