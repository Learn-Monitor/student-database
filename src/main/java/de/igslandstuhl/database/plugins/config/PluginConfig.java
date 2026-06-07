package de.igslandstuhl.database.plugins.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import de.igslandstuhl.database.plugins.Plugin;
import de.igslandstuhl.database.plugins.PluginLoader;

public abstract class PluginConfig<T extends Plugin> {
    private final T plugin;
    private final BoolSetting[] boolSettings;

    private final File configFile;
    private boolean enabledOnStart;

    public PluginConfig(T plugin, PluginSetting<?>... moduleSettings) {
        this.plugin = plugin;
        List<BoolSetting> boolSettings = Arrays.stream(moduleSettings).filter((s) -> s instanceof BoolSetting).map((s) -> (BoolSetting) s).toList();
        this.boolSettings = boolSettings.toArray(new BoolSetting[boolSettings.size()]);
        this.configFile = new File("plugins/config", plugin.getId() + ".json");
        load();
    }

    public PluginConfig(T plugin, List<PluginSetting<?>> moduleSettings) {
        this.plugin = plugin;
        List<BoolSetting> boolSettings = moduleSettings.stream().filter((s) -> s instanceof BoolSetting).map((s) -> (BoolSetting) s).toList();
        this.boolSettings = boolSettings.toArray(new BoolSetting[boolSettings.size()]);
        this.configFile = new File("plugins/config", plugin.getId() + ".json");
        load();
    }

    public T getPlugin() {
        return plugin;
    }
    public boolean isEnabledOnStart() {
        return enabledOnStart;
    }

    private BoolSetting findBoolSetting(String key) {
        return Arrays.stream(boolSettings)
        .filter((s) -> s.getKey().equals(key))
        .findAny().orElseThrow();
    }

    public boolean getBool(String key) {
        return findBoolSetting(key).getValue();
    }

    public void setBool(String key, boolean value) {
        findBoolSetting(key).setValue(value);
        save();
    }
    public void enableSetting(String key) {
        findBoolSetting(key).enable();
        save();
    }
    public void disableSetting(String key) {
        findBoolSetting(key).disable();
        save();
    }
    public void toggleSetting(String key) {
        findBoolSetting(key).toggle();
        save();
    }
    
    private JsonObject valuesJSON() {
        JsonObject values = new JsonObject();
        for (BoolSetting s : boolSettings) {
            values.addProperty(s.getKey(), s.getValue());
        }
        return values;
    }
    public String toJSON() {
        StringBuilder builder = new StringBuilder("{");
        builder.append("\"settings\": {")
        .append("\"bools\": [");
        for (int i = 0; i < boolSettings.length; i++) {
            builder.append(boolSettings[i].toJSON());
            if (i < boolSettings.length - 1) {
                builder.append(", ");
            }
        }
        builder
        .append("]}, ")
        .append("\"values\": ")
        .append((new Gson()).toJson(valuesJSON()))
        .append("}");
        return builder.toString();
    }

    // Persistence
    public void save() {
        if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            JsonObject root = new JsonObject();
            root.add("values", valuesJSON());
            root.addProperty("enabled", plugin.isEnabled());

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(root, writer);
        } catch (IOException e) {
            PluginLoader.LOGGER.error("Failed to save plugin config for {}", plugin.getId(), e);
        }
    }
    public void load() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(reader, JsonObject.class);

            JsonObject values = root.getAsJsonObject("values");
            if (values != null) {
                for (BoolSetting s : boolSettings) {
                    if (values.has(s.getKey())) {
                        s.setValue(values.get(s.getKey()).getAsBoolean());
                    }
                }
            }
            enabledOnStart = root.get("enabled").getAsBoolean();

        } catch (IOException e) {
            PluginLoader.LOGGER.error("Failed to load plugin config for {}", plugin.getId(), e);
        }
    }
}