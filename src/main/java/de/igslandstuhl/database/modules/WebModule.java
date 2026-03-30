package de.igslandstuhl.database.modules;

import java.util.List;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.modules.config.BoolSetting;
import de.igslandstuhl.database.modules.config.ModuleConfig;
import de.igslandstuhl.database.modules.config.ModuleSetting;

public abstract class WebModule {
    private String id;
    private String name;
    private String description;

    private boolean enabled;

    public WebModule(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.enabled = true;
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

    public abstract ModuleConfig<?> getConfig();

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
    public void load() {
        onLoad();
    }

    private static class DummyModule extends WebModule {
        private final ModuleConfig<DummyModule> config;
        public DummyModule(String id, String name, String description, List<ModuleSetting<?>> settings) {
            super(id, name, description);
            config = new ModuleConfig<WebModule.DummyModule>(this, settings) {
                
            };
        }

        @Override
        public ModuleConfig<?> getConfig() {
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

    private static void registerModule(WebModule module) {
        Registry.moduleRegistry().register(module.getId(), module);
    }
    public static void registerModules() {
        registerModule(new DummyModule("result_view", "Student Results View", "The view displaying the student's current progress and prognoses for the final result", List.of(
            new BoolSetting("show_prognosis", "Show Prognosis", "Whether to display the prognosis for the final result", true),
            new BoolSetting("show_current_progress", "Show Current", "Whether to display the current progress to the subject (in percent)", true),
            new BoolSetting("show_current_grade", "Show Currently Achieved Grade", "Whether to display the grade the student would achieve when they decide to immediately stop working", false)
        )));
    }

}
