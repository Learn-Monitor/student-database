package de.igslandstuhl.database.modules;

import java.net.URLClassLoader;

record PreLoadedModule (
    ModuleDescription description,
    Class<?> clazz,
    URLClassLoader classLoader
) {

}