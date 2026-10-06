package de.igslandstuhl.database;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class Arguments {
    private final Argument[] args;

    public Arguments(String[] args) {
        List<Argument> list = new LinkedList<>();
        for (int i = 0; i < args.length; i++) {
            String current = args[i];
            if (current.startsWith("--")) {
                String key = current.substring(2); // "--key" -> "key"

                // prüfen, ob ein Wert folgt
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    String value = args[++i]; // nächstes Element als Value nehmen
                    list.add(new Argument(key, value));
                } else {
                    list.add(new Argument(key)); // nur Key
                }
            }
        }
        this.args = list.toArray(new Argument[0]);
    }

    public String get(String key) {
        for (Argument argument : args) {
            if (argument.key().equals(key)) {
                return argument.value() == null ? "true" : argument.value();
            }
        }
        return null;
    }

    /**
     * Returns whether the option was followed by an explicit value.
     * Boolean-style flags continue to be represented by {@link #get(String)}
     * as {@code "true"}, while callers that require a value can distinguish
     * them from an option with a missing value.
     */
    public boolean hasValue(String key) {
        return Arrays.stream(args)
                .filter((arg) -> arg.key().equals(key))
                .anyMatch((arg) -> arg.value() != null);
    }
    public boolean hasKey(String key) {
        return Arrays.stream(args).anyMatch((arg) -> arg.key().equals(key));
    }
}
