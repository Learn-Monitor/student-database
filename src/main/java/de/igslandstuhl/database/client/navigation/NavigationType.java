package de.igslandstuhl.database.client.navigation;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.utils.RegistryEnum;

public class NavigationType extends RegistryEnum<NavigationType> {
    protected NavigationType(Registry<String, NavigationType> registry, String key) {
        super(registry, key);
    }

    private static final ResourceLocation meta = new ResourceLocation("meta", "navigation", "navigation_types.json");
    @Override
    protected NavigationType[] values(Registry<String, NavigationType> registry) {
        List<NavigationType> navigationTypes = registry.stream().toList();
        NavigationType[] arr = new NavigationType[navigationTypes.size()];
        return navigationTypes.toArray(arr);
    }

    @Override
    protected void initValues() {
        initUsingJSONMeta(meta);
    }

    @Override
    protected NavigationType initValue(Registry<String, NavigationType> registry,String key) {
        return new NavigationType(registry, key);
    }
    
    public static NavigationType valueOf(String string) {
        return RegistryEnum.valueOf(string, NavigationType.class);
    }
    public static void init() {
        try {
            RegistryEnum.init(NavigationType.class);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
