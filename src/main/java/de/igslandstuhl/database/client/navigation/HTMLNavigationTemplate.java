package de.igslandstuhl.database.client.navigation;

import java.util.Map;
import java.util.Comparator;
import java.util.List;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.client.HTMLTemplate;

public record HTMLNavigationTemplate(NavigationAppearance appearance, NavigationType type) implements HTMLTemplate {
    @Override
    public String fill(Map<String, String> args) {
        // args are not being used by this template
        List<NavigationElement> elements = Registry.navigationRegistry()
                .stream(type())
                .toList();
        if (type() == NavigationType.valueOf("TEACHER_DASHBOARD")) {
            elements = elements.stream()
                    .sorted(Comparator
                            .comparingInt(HTMLNavigationTemplate::teacherOrder)
                            .thenComparing(NavigationElement::path)
                            .thenComparing(NavigationElement::label))
                    .toList();
        }
        return appearance().translateToHTML(elements);
    }

    private static int teacherOrder(NavigationElement element) {
        return switch (element.path()) {
            case "/dashboard#overview" -> 0;
            case "/dashboard#curriculum" -> 1;
            case "/dashboard#student-progress" -> 2;
            case "/dashboard#tutor-area" -> 3;
            case "/attendance" -> 4;
            default -> 5;
        };
    }
}
