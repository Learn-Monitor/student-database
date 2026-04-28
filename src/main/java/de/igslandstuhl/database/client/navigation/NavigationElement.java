package de.igslandstuhl.database.client.navigation;

import java.util.List;
import java.util.Map;

import com.google.gson.reflect.TypeToken;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.resources.ResourceLocation;

public record NavigationElement(String path, String label) {
    private static final ResourceLocation meta = new ResourceLocation("meta", "navigation", "navigation_elements.json");

    public static void registerAll() {
        List<Map<String,String>> elements = Server.getInstance().getResourceManager().readJsonListMerged(meta, new TypeToken<List<Map<String,String>>>() {});
        elements.forEach((e) -> {
            Registry.navigationRegistry().register(NavigationType.valueOf(e.get("type")), new NavigationElement(e.get("path"), e.get("label")));
        });
    }
}
