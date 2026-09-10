package de.igslandstuhl.database.api.curriculum;

import java.util.List;

/** A client-safe error; never contains SQL or private user data. */
public class CurriculumException extends RuntimeException {
    public final int status;
    public final String code;
    public final List<Curriculum.Budget> affectedContexts;

    public CurriculumException(int status, String code, String message) {
        this(status, code, message, List.of());
    }
    public CurriculumException(int status, String code, String message, List<Curriculum.Budget> contexts) {
        super(message);
        this.status = status;
        this.code = code;
        this.affectedContexts = List.copyOf(contexts);
    }
}
