package de.igslandstuhl.database.plugins.config;

public class ShortAnswerSetting extends PluginSetting<String> {
    public ShortAnswerSetting(String key, String name, String description, String defaultValue) {
        super(key, name, description, defaultValue);
    }

    @Override
    public String toJSON() {
        return "{" +
                "\"key\":\"" + getKey() + "\"," +
                "\"name\":\"" + getName() + "\"," +
                "\"description\":\"" + getDescription() + "\"," +
                "\"defaultValue\":\"" + getDefaultValue() + "\"," +
                "\"value\":\"" + getValue() + "\"" +
                "}";
    }
}
