package de.igslandstuhl.database.api.modules;

import java.util.LinkedList;
import java.util.List;

import de.igslandstuhl.database.Registry;

public abstract class WebModule {
    private String id;
    private String name;
    private String description;

    private boolean enabled;

    private List<ModuleSetting<?>> settings = new LinkedList<>();

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
        sb.append("\"settings\":{");
        for (int i = 0; i < settings.size(); i++) {
            sb.append("\"").append(settings.get(i).getKey()).append("\":").append(settings.get(i).toJSON());
            if (i < settings.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    public List<ModuleSetting<?>> getSettings() {
        return settings;
    }
    public ModuleSetting<?> getSettingByKey(String key) {
        for (ModuleSetting<?> setting : settings) {
            if (setting.getKey().equals(key)) {
                return setting;
            }
        }
        return null;
    }
    public BoolSetting getBoolSetting(String key) {
        ModuleSetting<?> setting = getSettingByKey(key);
        if (setting instanceof BoolSetting) {
            return (BoolSetting) setting;
        }
        return null;
    }

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

    public void toggleSetting(String key) {
        getBoolSetting(key).toggle();
    }

    private static class DummyModule extends WebModule {
        public DummyModule(String id, String name, String description, List<ModuleSetting<?>> settings) {
            super(id, name, description);
            this.getSettings().addAll(settings);
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
