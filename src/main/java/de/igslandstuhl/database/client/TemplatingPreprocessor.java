package de.igslandstuhl.database.client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import de.igslandstuhl.database.Registry;

public class TemplatingPreprocessor {
    private static final TemplatingPreprocessor instance = new TemplatingPreprocessor();
    public static TemplatingPreprocessor getInstance() {
        return instance;
    }

    private TemplatingPreprocessor() {}

    private HTMLTemplate getTemplate(String name) throws FileNotFoundException {
        return Registry.templateRegistry().get(name);
    }

    public String executeTemplating(String content) throws IOException {
        if (content == null || content.isEmpty()) return content;

        StringBuilder out = new StringBuilder();
        int idx = 0;
        while (idx < content.length()) {
            int start = content.indexOf("%[", idx);
            if (start < 0) {
                // no more templates
                out.append(content.substring(idx));
                break;
            }
            // append prefix
            out.append(content.substring(idx, start));
            int end = content.indexOf("]", start + 2);
            if (end < 0) {
                throw new IOException("Unclosed template starting at " + start);
            }
            String inside = content.substring(start + 2, end).trim();
            if (inside.isEmpty()) {
                idx = end + 1;
                continue;
            }

            // parse name and args
            String[] parts = inside.split(";");
            String templateName = parts[0].trim();
            Map<String, String> args = new HashMap<>();
            boolean usesFollows = false;
            String followsKey = null;

            for (int i = 1; i < parts.length; i++) {
                String part = parts[i];
                int eq = part.indexOf('=');
                if (eq <= 0) continue;
                String key = part.substring(0, eq).trim();
                String val = part.substring(eq + 1).trim();
                if ("!FOLLOWS".equals(val)) {
                    // the rest of the content is the value for this key
                    usesFollows = true;
                    followsKey = key;
                    break;
                } else {
                    // expand templates inside argument values
                    String expandedVal = executeTemplating(val);
                    args.put(key, expandedVal);
                }
            }

            if (usesFollows) {
                // the value is the rest of the content after the closing ']'
                String rest = content.substring(end + 1);
                // allow templates inside the follows value
                String expandedRest = executeTemplating(rest);
                args.put(followsKey, expandedRemapNull(expandedRest));
                // build template and finish (rest is consumed by this template)
                HTMLTemplate template = getTemplate(templateName);
                String filled = template.fill(args);
                // expand any templates produced by the filled template
                String finalFilled = executeTemplating(filled);
                out.append(finalFilled);
                // consumed entire remaining content
                idx = content.length();
                break;
            } else {
                // normal case: build template now, then continue after marker
                HTMLTemplate template = getTemplate(templateName);
                String filled = template.fill(args);
                // expand templates that might be present inside the filled template
                String finalFilled = executeTemplating(filled);
                out.append(finalFilled);
                idx = end + 1;
            }
        }

        return out.toString();
    }

    // helper in case expanded rest is null
    private static String expandedRemapNull(String s) {
        return s == null ? "" : s;
    }
}
// ...existing code...