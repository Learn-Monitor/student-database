package de.igslandstuhl.database.server.webserver.responses;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.resources.ResourceLocation;

public class TemplatingPreprocessor {
    private static final TemplatingPreprocessor instance = new TemplatingPreprocessor();
    public static TemplatingPreprocessor getInstance() {
        return instance;
    }

    private TemplatingPreprocessor() {}

    private String getTemplate(String name) throws FileNotFoundException {
        ResourceLocation templateLocation = new ResourceLocation("templates", "html", name + ".html");
        return Server.getInstance().getResourceManager().readResourceCompletely(templateLocation);
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
                String template = getTemplate(templateName);
                String filled = fillTemplate(template, args);
                // expand any templates produced by the filled template
                String finalFilled = executeTemplating(filled);
                out.append(finalFilled);
                // consumed entire remaining content
                idx = content.length();
                break;
            } else {
                // normal case: build template now, then continue after marker
                String template = getTemplate(templateName);
                String filled = fillTemplate(template, args);
                // expand templates that might be present inside the filled template
                String finalFilled = executeTemplating(filled);
                out.append(finalFilled);
                idx = end + 1;
            }
        }

        return out.toString();
    }

    // replace %{key} with args.getOrDefault(key, "")
    private static String fillTemplate(String template, Map<String, String> args) {
        if (template == null || template.isEmpty()) return "";
        Pattern p = Pattern.compile("%\\{([^}]+)\\}");
        Matcher m = p.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String val = args.getOrDefault(key, "");
            // escape backslashes and dollars for regex replacement
            val = val.replace("\\", "\\\\").replace("$", "\\$");
            m.appendReplacement(sb, val);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // helper in case expanded rest is null
    private static String expandedRemapNull(String s) {
        return s == null ? "" : s;
    }
}
// ...existing code...