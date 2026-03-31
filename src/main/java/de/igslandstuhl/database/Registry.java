package de.igslandstuhl.database;

import java.io.Closeable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import de.igslandstuhl.database.client.HTMLTemplate;
import de.igslandstuhl.database.client.dynamic.DynamicFieldType;
import de.igslandstuhl.database.client.navigation.NavigationElement;
import de.igslandstuhl.database.client.navigation.NavigationType;
import de.igslandstuhl.database.plugins.Plugin;
import de.igslandstuhl.database.server.commands.Command;
import de.igslandstuhl.database.server.commands.CommandDescription;
import de.igslandstuhl.database.server.webserver.WebPath;
import de.igslandstuhl.database.server.webserver.handlers.HttpHandler;
import de.igslandstuhl.database.server.webserver.requests.APIPostRequest;
import de.igslandstuhl.database.server.webserver.requests.GetRequest;
import de.igslandstuhl.database.utils.RegistryEnum;

public class Registry<K, V> implements Closeable {
    private static final Registry<String,Command> COMMAND_REGISTRY = new Registry<>();
    private static final Registry<String,CommandDescription> COMMAND_DESCRIPTION_REGISTRY = new Registry<>();
    private static final Registry<String,HttpHandler<APIPostRequest>> POST_HANDLER_REGISTRY = new Registry<>();
    private static final Registry<String,HttpHandler<GetRequest>> GET_HANDLER_REGISTRY = new Registry<>();
    private static final Registry<String,Plugin> PLUGIN_REGISTRY = new Registry<>();
    private static final Registry<String,WebPath> WEB_PATH_REGISTRY = new Registry<>();

    private static final EnumRegistry<NavigationType,NavigationElement> NAVIGATION_REGISTRY = new EnumRegistry<>(NavigationType.class);
    private static final EnumRegistry<DynamicFieldType,String> DYNAMIC_TEMPLATES_REGISTRY = new EnumRegistry<>(DynamicFieldType.class);
    private static final Registry<String,HTMLTemplate> TEMPLATE_REGISTRY = new Registry<>();

    public static Registry<String,Command> commandRegistry() {
        return COMMAND_REGISTRY;
    }
    public static Registry<String, HttpHandler<APIPostRequest>> postRequestHandlerRegistry() {
        return POST_HANDLER_REGISTRY;
    }
    public static Registry<String, HttpHandler<GetRequest>> getRequestHandlerRegistry() {
        return GET_HANDLER_REGISTRY;
    }
    public static Registry<String, Plugin> pluginRegistry() {
        return PLUGIN_REGISTRY;
    }
    public static Registry<String, CommandDescription> commandDescriptionRegistry() {
        return COMMAND_DESCRIPTION_REGISTRY;
    }
    public static Registry<String, WebPath> webPathRegistry() {
        return WEB_PATH_REGISTRY;
    }
    public static EnumRegistry<NavigationType, NavigationElement> navigationRegistry() {
        return NAVIGATION_REGISTRY;
    }
    public static EnumRegistry<DynamicFieldType, String> dynamicTemplatesRegistry() {
        return DYNAMIC_TEMPLATES_REGISTRY;
    }
    public static Registry<String, HTMLTemplate> templateRegistry() {
        return TEMPLATE_REGISTRY;
    }

    private final Map<K,V> objects = new HashMap<>();
    private boolean locked = false;

    private void checkLocked() {
        if (locked) throw new RegistryLockedException();
    }
    public synchronized void register(K key, V value) {
        checkLocked();
        objects.put(key, value);
    }
    public synchronized Stream<V> stream() {
        return objects.values().stream();
    }
    public synchronized Stream<K> keyStream() {
        return objects.keySet().stream();
    }
    public synchronized V get(K key) {
        return objects.get(key);
    }
    public synchronized void unregister(K key) {
        objects.remove(key);
    }
    public synchronized void lock() {
        locked = true;
    }

    public static class EnumRegistry<K extends RegistryEnum<K>, V> {
        private final Map<K,Set<V>> objects = new HashMap<>();

        private EnumRegistry(Class<K> clazz) {
            try {
                Registry<String,K> enumRegistry = RegistryEnum.init(clazz);
                enumRegistry.stream().forEach((k) -> objects.put(k, new HashSet<>()));
            } catch (Exception e) {
                throw new RuntimeException("Could not initialize registry enum " + clazz.getName(), e);
            }
        }
        public synchronized void register(K key, V value) {
            objects.get(key).add(value);
        }
        public synchronized Stream<V> stream(K key) {
            return objects.get(key).stream();
        }
        public synchronized Stream<K> keyStream() {
            return objects.keySet().stream();
        }
        public synchronized void unregister(K key) {
            objects.get(key).clear();
        }
    }

    @Override
    public void close() {
        objects.clear();
    }
}
