package de.igslandstuhl.database.plugins.config;

import java.util.Arrays;
import java.util.List;

import de.igslandstuhl.database.plugins.Plugin;

public abstract class PluginConfig<T extends Plugin> {
    private final T plugin;

    private final BoolSetting[] boolSettings;

    public PluginConfig(T plugin, PluginSetting<?>[] moduleSettings) {
        this.plugin = plugin;
        List<BoolSetting> boolSettings = Arrays.stream(moduleSettings).filter((s) -> s instanceof BoolSetting).map((s) -> (BoolSetting) s).toList();
        this.boolSettings = boolSettings.toArray(new BoolSetting[boolSettings.size()]);
    }

    public PluginConfig(T plugin, List<PluginSetting<?>> moduleSettings) {
        this.plugin = plugin;
        List<BoolSetting> boolSettings = moduleSettings.stream().filter((s) -> s instanceof BoolSetting).map((s) -> (BoolSetting) s).toList();
        this.boolSettings = boolSettings.toArray(new BoolSetting[boolSettings.size()]);
    }

    public T getPlugin() {
        return plugin;
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
    }
    public void enableSetting(String key) {
        findBoolSetting(key).enable();
    }
    public void disableSetting(String key) {
        findBoolSetting(key).disable();
    }
    public void toggleSetting(String key) {
        findBoolSetting(key).toggle();
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
        .append("\"values\": {");
        for (int i = 0; i < boolSettings.length; i++) {
            builder
            .append("\"")
            .append(boolSettings[i].getKey())
            .append("\": ")
            .append(boolSettings[i].getValue());
            if (i < boolSettings.length - 1) {
                builder.append(", ");
            }
        }
        builder.append("}}");
        return builder.toString();
    }
}