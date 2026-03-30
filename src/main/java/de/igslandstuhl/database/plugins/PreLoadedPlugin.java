package de.igslandstuhl.database.plugins;

import java.net.URLClassLoader;

record PreLoadedPlugin (
    PluginDescription description,
    Class<?> clazz,
    URLClassLoader classLoader
) {

}