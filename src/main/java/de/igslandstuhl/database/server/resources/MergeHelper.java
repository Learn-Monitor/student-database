package de.igslandstuhl.database.server.resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class MergeHelper {
    public static Map<String, ?> readJsonObjectMerged(ResourceManager manager, ResourceLocation location) {
        Gson gson = new Gson();

        return manager.mergeResources(location,
            // merger
            (a, b) -> {
                a.putAll(b);
                return a;
            },
            //start supplier
            () -> (Map<String,Object>)new LinkedHashMap<String, Object>(),
            // parser
            (is) -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    return gson.fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        );
    }
    public static <T> List<T> readJsonListMerged(ResourceManager manager, ResourceLocation location, TypeToken<List<T>> listType) {
        Gson gson = new Gson();

        return manager.mergeResources(
            location,

            // merger
            (a, b) -> {
                a.addAll(b);
                return a;
            },

            // supplier
            ArrayList::new,

            // parser
            (is) -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    return gson.fromJson(reader, listType.getType());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        );
    }
    public static String readMergedCode(ResourceManager manager, ResourceLocation location) {
        return manager.mergeResources(
            location,

            // merger
            (a, b) -> b + "\n" + a, // concat backwards

            // supplier
            () -> "",

            // parser
            (is) -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    return reader.lines().reduce("", (acc, line) -> acc + line + "\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        );
    }
}
