package de.igslandstuhl.database.server.webserver.access;

import de.igslandstuhl.database.events.EventFilter;

public class AccessStateFilter implements EventFilter<AccessManagerEvent> {
    private final AccessState state;

    public AccessStateFilter(AccessState state) {
        this.state = state;
    }

    @Override
    public boolean filter(AccessManagerEvent event) {
        return event.getAccessState() == state;
    }
}
