package de.igslandstuhl.database.plugins.config;

public class BoolSetting extends PluginSetting<Boolean> {
    public BoolSetting(String key, String name, String description, boolean defaultValue) {
        super(key, name, description, defaultValue);
    }

    public void toggle() {
        setValue(!getValue());
    }
    public void enable() {
        setValue(true);
    }
    public void disable() {
        setValue(false);
    }
    public boolean isEnabled() {
        return getValue();
    }

    @Override
    public String toJSON() {
        return "{" +
                "\"key\":\"" + getKey() + "\"," +
                "\"name\":\"" + getName() + "\"," +
                "\"description\":\"" + getDescription() + "\"," +
                "\"defaultValue\":" + getDefaultValue() + "," +
                "\"value\":" + getValue() +
                "}";
    }
}
