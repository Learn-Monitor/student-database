package de.igslandstuhl.database.plugins.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import de.igslandstuhl.database.plugins.Plugin;
import de.igslandstuhl.database.plugins.PluginLoader;

public abstract class PluginConfig<T extends Plugin> {
    private final T plugin;
    private final BoolSetting[] boolSettings;
    private final IntSetting[] intSettings;
    private final ShortAnswerSetting[] shortAnswerSettings;

    private final File configFile;
    private boolean enabledOnStart;

    public PluginConfig(T plugin, PluginSetting<?>... moduleSettings) {
        this(plugin, Arrays.asList(moduleSettings));
    }

    public PluginConfig(T plugin, List<PluginSetting<?>> moduleSettings) {
        this.plugin = plugin;
        List<BoolSetting> boolSettings = moduleSettings.stream().filter((s) -> s instanceof BoolSetting).map((s) -> (BoolSetting) s).toList();
        this.boolSettings = boolSettings.toArray(new BoolSetting[boolSettings.size()]);
        List<IntSetting> intSettings = moduleSettings.stream().filter((s) -> s instanceof IntSetting).map((s) -> (IntSetting) s).toList();
        this.intSettings = intSettings.toArray(new IntSetting[intSettings.size()]);
        List<ShortAnswerSetting> shortAnswerSettings = moduleSettings.stream().filter((s) -> s instanceof ShortAnswerSetting).map((s) -> (ShortAnswerSetting) s).toList();
        this.shortAnswerSettings = shortAnswerSettings.toArray(new ShortAnswerSetting[shortAnswerSettings.size()]);
        this.configFile = new File("plugins/config", plugin.getId() + ".json");
        load();
    }

    public T getPlugin() {
        return plugin;
    }
    public boolean isEnabledOnStart() {
        return enabledOnStart;
    }

    public PluginSetting<?> getSetting(String key) {
        Optional<PluginSetting<?>> boolSetting = findBoolSetting(key).map((s) -> (PluginSetting<?>) s);
        if (boolSetting.isPresent()) {
            return boolSetting.get();
        }
        Optional<PluginSetting<?>> intSetting = findIntSetting(key).map((s) -> (PluginSetting<?>) s);
        if (intSetting.isPresent()) {
            return intSetting.get();
        }
        return findShortAnswerSetting(key).map((s) -> (PluginSetting<?>) s).orElse(null);
    }

    private Optional<BoolSetting> findBoolSetting(String key) {
        return Arrays.stream(boolSettings)
        .filter((s) -> s.getKey().equals(key))
        .findAny();
    }
    public boolean getBool(String key) {
        return findBoolSetting(key).map(BoolSetting::getValue).orElse(false);
    }
    public void setBool(String key, boolean value) {
        findBoolSetting(key).ifPresent((s) -> s.setValue(value));
        save();
    }
    public void enableSetting(String key) {
        findBoolSetting(key).ifPresent(BoolSetting::enable);
        save();
    }
    public void disableSetting(String key) {
        findBoolSetting(key).ifPresent(BoolSetting::disable);
        save();
    }
    public void toggleSetting(String key) {
        findBoolSetting(key).ifPresent(BoolSetting::toggle);
        save();
    }

    private Optional<IntSetting> findIntSetting(String key) {
        return Arrays.stream(intSettings)
        .filter((s) -> s.getKey().equals(key))
        .findAny();
    }
    private Optional<ShortAnswerSetting> findShortAnswerSetting(String key) {
        return Arrays.stream(shortAnswerSettings)
        .filter((s) -> s.getKey().equals(key))
        .findAny();
    }
    
    private JsonObject valuesJSON() {
        JsonObject values = new JsonObject();
        for (BoolSetting s : boolSettings) {
            values.addProperty(s.getKey(), s.getValue());
        }
        for (IntSetting s : intSettings) {
            values.addProperty(s.getKey(), s.getValue());
        }
        for (ShortAnswerSetting s : shortAnswerSettings) {
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
        .append("], ")
        .append("\"ints\": [");
        for (int i = 0; i < intSettings.length; i++) {
            builder.append(intSettings[i].toJSON());
            if (i < intSettings.length - 1) {
                builder.append(", ");
            }
        }
        builder
        .append("], ")
        .append("\"shortAnswers\": [");
        for (int i = 0; i < shortAnswerSettings.length; i++) {
            builder.append(shortAnswerSettings[i].toJSON());
            if (i < shortAnswerSettings.length - 1) {
                builder.append(", ");
            }
        }
        builder
        .append("},")
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
                for (IntSetting s : intSettings) {
                    if (values.has(s.getKey())) {
                        s.setValue(values.get(s.getKey()).getAsInt());
                    }
                }
                for (ShortAnswerSetting s : shortAnswerSettings) {
                    if (values.has(s.getKey())) {
                        s.setValue(values.get(s.getKey()).getAsString());
                    }
                }
            }
            enabledOnStart = root.get("enabled").getAsBoolean();

        } catch (IOException e) {
            PluginLoader.LOGGER.error("Failed to load plugin config for {}", plugin.getId(), e);
        }
    }
}