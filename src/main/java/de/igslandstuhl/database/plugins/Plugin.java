package de.igslandstuhl.database.plugins;

import java.util.List;

import de.igslandstuhl.database.plugins.config.PluginConfig;
import de.igslandstuhl.database.plugins.config.PluginSetting;

public abstract class Plugin {
    private String id;
    private String name;
    private String description;

    private boolean enabled;
    private boolean initialized = false;

    public Plugin() {
        this.enabled = false;
    }

    void init(String id, String name, String description) {
        if (initialized) throw new IllegalStateException("Plugin already initialized");
        this.id = id;
        this.name = name;
        this.description = description;
        this.initialized = true;
    }
    void init(PluginDescription description) {
        init(description.id(), description.name(), description.description());
    }

    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public boolean isEnabled() {
        return enabled;
    }

    public String toJSON() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"name\":\"").append(name).append("\",");
        sb.append("\"description\":\"").append(description).append("\",");
        sb.append("\"enabled\":").append(enabled).append(",");
        sb.append("\"config\":").append(getConfig().toJSON());
        sb.append("}");
        return sb.toString();
    }

    public abstract PluginConfig<?> getConfig();

    protected abstract void onEnable();
    protected abstract void onDisable();
    protected abstract void onLoad();

    public void enable() {
        if (enabled) return;
        onEnable();
        enabled = true;
    }
    public void disable() {
        if (!enabled) return;
        onDisable();
        enabled = false;
    }
    public void toggle() {
        if (enabled) {
            disable();
        } else {
            enable();
        }
    }
    void load() {
        onLoad();
    }

    static class DummyModule extends Plugin {
        private final PluginConfig<DummyModule> config;
        public DummyModule(String id, String name, String description, List<PluginSetting<?>> settings) {
            init(id, name, description);
            config = new PluginConfig<Plugin.DummyModule>(this, settings) {
                
            };
        }

        @Override
        public PluginConfig<?> getConfig() {
            return config;
        }

        @Override
        protected void onEnable() {
            // Dummy enable logic
        }

        @Override
        protected void onDisable() {
            // Dummy disable logic
        }

        @Override
        protected void onLoad() {
            // Dummy load logic
        }
    }

}
