package de.igslandstuhl.database.client.dynamic;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.utils.RegistryEnum;

public class DynamicFieldType extends RegistryEnum<DynamicFieldType> {
    protected DynamicFieldType(Registry<String, DynamicFieldType> registry, String key) {
        super(registry, key);
    }

    private static final ResourceLocation meta = new ResourceLocation("meta", "dynamic", "dynamic_field_types.json");
    @Override
    protected DynamicFieldType[] values(Registry<String, DynamicFieldType> registry) {
        List<DynamicFieldType> DynamicFieldTypes = registry.stream().toList();
        DynamicFieldType[] arr = new DynamicFieldType[DynamicFieldTypes.size()];
        return DynamicFieldTypes.toArray(arr);
    }

    @Override
    protected void initValues() {
        initUsingJSONMeta(meta);
    }

    @Override
    protected DynamicFieldType initValue(Registry<String, DynamicFieldType> registry,String key) {
        return new DynamicFieldType(registry, key);
    }
    
    public static DynamicFieldType valueOf(String string) {
        return RegistryEnum.valueOf(string, DynamicFieldType.class);
    }
    public static void init() {
        try {
            RegistryEnum.init(DynamicFieldType.class);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
