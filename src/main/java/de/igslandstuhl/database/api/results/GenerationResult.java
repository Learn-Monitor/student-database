package de.igslandstuhl.database.api.results;

/**
 * Represents the result of a generation process for an user of type T.
 * @param <T> the type of the generated entity
 */
public abstract class GenerationResult<T> {
    private final T entity;
    private final String password;

    /**
     * Constructs a GenerationResult with the specified entity and password.
     * @param entity the generated entity of type T
     * @param password the password associated with the generated entity
     */
    public GenerationResult(T entity, String password) {
        this.entity = entity;
        this.password = password;
    }

    /**
     * Returns the generated entity of type T.
     * @return the generated entity
     */
    public T getEntity() {
        return entity;
    }

    /**
     * Returns the corresponding password associated with the generated entity.
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns a CSV representation of the generation result.
     * @return a CSV representation of the generation result.
     */
    public abstract String toCSVRow();
}
