package de.igslandstuhl.database.client.navigation;

import java.util.List;
import java.util.function.Function;

public enum NavigationAppearance {
    LIST_APPEARANCE ((l) -> {
        StringBuilder builder = new StringBuilder("<ul>");
        l.forEach((e) -> {
            builder.append("<li>")
            .append("<a href=\"").append(e.path()).append("\">")
            .append(e.label())
            .append("</a>")
            .append("</li>")
            ;
        });
        builder.append("</ul>");
        return builder.toString();
    }),
    BUTTON_APPEARANCE ((l) -> {
        StringBuilder builder = new StringBuilder();
        l.forEach((e) -> {
            builder.append("<a href=\"").append(e.path()).append("\">")
            .append("<button>")
            .append(e.label())
            .append("</button></a>");
        });
        return builder.toString();
    });
    private final Function<List<NavigationElement>,String> translator;
    private NavigationAppearance(Function<List<NavigationElement>,String> translator) {
        this.translator = translator;
    }
    public String translateToHTML(List<NavigationElement> navigationElements) {
        return translator.apply(navigationElements);
    }
}
