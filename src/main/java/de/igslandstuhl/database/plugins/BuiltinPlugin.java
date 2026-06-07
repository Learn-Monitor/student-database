package de.igslandstuhl.database.plugins;

public abstract class BuiltinPlugin extends Plugin {
    protected BuiltinPlugin(PluginDescription description) {
        super();
        init(description);
    }
}
