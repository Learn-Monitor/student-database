package de.igslandstuhl.database.server.commands;

import de.igslandstuhl.database.utils.StringUtils;

public record CommandDescription(String name, String description, String usage) {
    public String toString() {
        return StringUtils.fitSize(name, 20) + "\t" + StringUtils.fitSize(description, 50) + "\t" + StringUtils.fitSize(usage, 30);
    }
}
