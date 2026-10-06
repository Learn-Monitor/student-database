package de.igslandstuhl.database;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ArgumentsTest {
    @Test
    void distinguishesMissingValueFromExplicitValue() {
        Arguments arguments = new Arguments(new String[] {"--missing", "--value", "true"});

        assertEquals("true", arguments.get("missing"));
        assertFalse(arguments.hasValue("missing"));
        assertTrue(arguments.hasValue("value"));
    }
}
