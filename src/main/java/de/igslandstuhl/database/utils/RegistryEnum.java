package de.igslandstuhl.database.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.reflect.TypeToken;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.plugins.PluginResourceProvider;
import de.igslandstuhl.database.server.resources.CoreResourceProvider;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.resources.ResourceManager;

public abstract class RegistryEnum<T extends RegistryEnum<T>> {
    private static final Map<Class<?>,Registry<String,?>> map = new HashMap<>();
    private static final ResourceManager manager = new ResourceManager(new PluginResourceProvider(), new CoreResourceProvider());
    @SuppressWarnings("unchecked")
    public static <T extends RegistryEnum<T>> Registry<String,T> getRegistry(Class<T> clazz) {
        return (Registry<String,T>) map.get(clazz);
    }
    private final Registry<String,T> registry;
    private final String key;

    protected RegistryEnum(Registry<String,T> registry,String key) {
        this.registry = registry;
        this.key = key;
    }

    protected abstract T[] values(Registry<String,T> registry);

    public T[] values() {
        return values(registry);
    }

    protected abstract void initValues();

    protected abstract T initValue(Registry<String, T> registry, String key);

    protected void initUsingJSONMeta(ResourceLocation meta) {
        List<String> constants = manager.readJsonListMerged(meta, new TypeToken<List<String>>() {});
        constants.forEach((c) -> registry.register(c, initValue(registry, c)));
    }

    public static <T extends RegistryEnum<T>> Registry<String,T> init(Class<T> clazz) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        Registry<String,T> registry = new Registry<>();
        var constructor = clazz.getDeclaredConstructor(Registry.class, String.class);
        constructor.setAccessible(true);
        T dummy = constructor.newInstance(registry, "DUMMY");
        dummy.initValues();
        if (registry.stream().anyMatch((t) -> t == dummy)) registry.unregister(registry.keyStream().filter((k) -> registry.get(k) == dummy).findAny().get());
        registry.lock();
        map.put(clazz, registry);
        return registry;
    }
    public static <T extends RegistryEnum<T>> T valueOf(String s, Class<T> clazz) {
        return getRegistry(clazz).get(s);
    }

    @Override
    public String toString() {
        return key;
    }
}
