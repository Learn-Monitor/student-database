package de.igslandstuhl.database.api.modules;

public class ModuleSetting<T> {
    private final String key;
    private final String name;
    private final String description;
    private final T defaultValue;
    private T value;

    public ModuleSetting(String key, String name, String description, T defaultValue) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getKey() {
        return key;
    }
    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public T getDefaultValue() {
        return defaultValue;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public String toJSON() {
        return "{" +
                "\"key\":\"" + key + "\"," +
                "\"name\":\"" + name + "\"," +
                "\"description\":\"" + description + "\"," +
                "\"defaultValue\":\"" + defaultValue + "\"," +
                "\"value\":\"" + value + "\"" +
                "}";
    }
    
}
