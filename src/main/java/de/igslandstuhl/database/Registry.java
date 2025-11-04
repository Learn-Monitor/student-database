package de.igslandstuhl.database;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import de.igslandstuhl.database.server.commands.Command;
import de.igslandstuhl.database.server.commands.CommandDescription;
import de.igslandstuhl.database.server.webserver.handlers.HttpHandler;
import de.igslandstuhl.database.server.webserver.requests.APIPostRequest;
import de.igslandstuhl.database.server.webserver.requests.GetRequest;

public class Registry<K, V> implements Closeable {
    private static final Registry<String,Command> COMMAND_REGISTRY = new Registry<>();
    private static final Registry<String,CommandDescription> COMMAND_DESCRIPTION_REGISTRY = new Registry<>();
    private static final Registry<String,HttpHandler<APIPostRequest>> POST_HANDLER_REGISTRY = new Registry<>();
    private static final Registry<String,HttpHandler<GetRequest>> GET_HANDLER_REGISTRY = new Registry<>();
    public static Registry<String,Command> commandRegistry() {
        return COMMAND_REGISTRY;
    }
    public static Registry<String, HttpHandler<APIPostRequest>> postRequestHandlerRegistry() {
        return POST_HANDLER_REGISTRY;
    }
    public static Registry<String, HttpHandler<GetRequest>> getRequestHandlerRegistry() {
        return GET_HANDLER_REGISTRY;
    }
    public static Registry<String, CommandDescription> commandDescriptionRegistry() {
        return COMMAND_DESCRIPTION_REGISTRY;
    }

    private final Map<K,V> objects = new HashMap<>();

    public synchronized void register(K key, V value) {
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

    @Override
    public void close() {
        objects.clear();
    }
}
