package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class GraduationLevelTest {
    @Test
    public void testGermanTranslation() {
        assert GraduationLevel.LEVEL0.getGermanTranslation() != null;
        assert GraduationLevel.LEVEL1.getGermanTranslation() != null;
        assert GraduationLevel.LEVEL2.getGermanTranslation() != null;
        assert GraduationLevel.LEVEL3.getGermanTranslation() != null;

        assert GraduationLevel.LEVEL0.getGermanTranslation().equals(GraduationLevel.LEVEL0.toString());
        assert GraduationLevel.LEVEL1.getGermanTranslation().equals(GraduationLevel.LEVEL1.toString());
        assert GraduationLevel.LEVEL2.getGermanTranslation().equals(GraduationLevel.LEVEL2.toString());
        assert GraduationLevel.LEVEL3.getGermanTranslation().equals(GraduationLevel.LEVEL3.toString());
    }
    @Test
    public void testOfMethod() {
        assert GraduationLevel.of(0) == GraduationLevel.LEVEL0;
        assert GraduationLevel.of(1) == GraduationLevel.LEVEL1;
        assert GraduationLevel.of(2) == GraduationLevel.LEVEL2;
        assert GraduationLevel.of(3) == GraduationLevel.LEVEL3;

        assertThrows(IllegalArgumentException.class, () -> {
            GraduationLevel.of(4);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            GraduationLevel.of(-1);
        });
    }
    @Test
    public void testInitialValue() {
        assert GraduationLevel.initialValue() == GraduationLevel.LEVEL1;
    }
    @Test
    public void testLevel() {
        assert GraduationLevel.LEVEL0.getLevel() == 0;
        assert GraduationLevel.LEVEL1.getLevel() == 1;
        assert GraduationLevel.LEVEL2.getLevel() == 2;
        assert GraduationLevel.LEVEL3.getLevel() == 3;

        assert GraduationLevel.LEVEL0.toJSON().equals("0");
        assert GraduationLevel.LEVEL1.toJSON().equals("1");
        assert GraduationLevel.LEVEL2.toJSON().equals("2");
        assert GraduationLevel.LEVEL3.toJSON().equals("3");
    }
}
