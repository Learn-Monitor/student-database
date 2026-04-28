package de.igslandstuhl.database.server.sql;

import java.io.FileNotFoundException;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.resources.ResourceLocation;

/**
 * Helper class for SQL operations.
 */
public class SQLHelper {
    /**
     * Context for SQL resources.
     * This is used to identify the context of SQL queries and pushes.
     */
    public static final String CONTEXT = "sql";
    /**
     * Subdirectory for SQL queries.
     * This is used to organize SQL query files.
     */
    public static final String QUERIES = "queries";
    /**
     * Subdirectory for SQL pushes.
     * This is used to organize SQL push files.
     */
    public static final String PUSHES = "pushes";
    public static void insertArgs(PreparedStatement s, String[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            try {
                int intArg = Integer.parseInt(arg);
                s.setInt(i + 1, intArg);
            } catch (NumberFormatException e) {
                s.setString(i + 1, arg);
            }
        }
    }
    /**
     * Gets an SQL query by its name and replaces placeholders with provided arguments.
     *
     * @param queryName the name of the SQL query file (without extension)
     * @param args      the arguments to replace in the query
     * @return the SQL query as a String with placeholders replaced
     */
    public static String getSQLQuery(String queryName) {
        ResourceLocation location = new ResourceLocation(CONTEXT, QUERIES, queryName + ".sql");
        String query;
        try {
            query = Server.getInstance().getResourceManager().readResourceCompletely(location);
        } catch (FileNotFoundException e) {
            throw new SQLCommandNotFoundException(queryName, e);
        }
        return query;
    }
    /**
     * Prepares an SQL query statement by its name and replaces placeholders with provided arguments.
     *
     * @param queryName the name of the SQL query file (without extension)
     * @param args      the arguments to replace in the query
     * @return a PreparedStatement for the SQL query
     * @throws SQLException if an error occurs while preparing the statement
     */
    public static PreparedStatement prepareSQLQuery(String queryName, String... args) throws SQLException {
        String query = getSQLQuery(queryName);
        // TODO: Add args
        return Server.getInstance().getConnection().prepareStatement(query);
    }
    /**
     * Gets an SQL query process by its name and replaces placeholders with provided arguments.
     *
     * @param queryName the name of the SQL query file (without extension)
     * @param args      the arguments to replace in the query
     * @return a SQLProcess that executes the query
     */
    public static SQLProcess getQueryProcess(String queryName, String... args) {
        String query = getSQLQuery(queryName);
        return new SQLQueryProcess(query, args);
    }

    /**
     * Gets an SQL statement for a specific type and object.
     * @param type the type of SQL operation (e.g., "add", "delete", "update")
     * @param object the name of the object (e.g., "student", "course")
     * @return the SQL statement as a String
     */
    private static String getSQLStatement(String type, String object) {
        ResourceLocation location = new ResourceLocation(CONTEXT, PUSHES, type + "_" + object + ".sql");
        String statement;
        try {
            statement = Server.getInstance().getResourceManager().readResourceCompletely(location);
        } catch (FileNotFoundException e) {
            throw new SQLCommandNotFoundException(type + "_" + object, e);
        }
        return statement;
    }

    /**
     * Gets an SQL process for adding an object to the database.
     *
     * @param object the name of the object to add (e.g., "student", "course")
     * @param args   the arguments to replace in the SQL statement
     * @return a SQLVoidProcess that executes the add statement
     */
    public static SQLVoidProcess getAddObjectProcess(String object, String... args) {
        return SQLVoidProcess.update(getSQLStatement("add", object), args);
    }

    /**
     * Gets an SQL process for deleting an object from the database.
     *
     * @param object the name of the object to add (e.g., "student", "course")
     * @param args   the arguments to replace in the SQL statement
     * @return a SQLVoidProcess that executes the add statement
     */
    public static SQLVoidProcess getDeleteObjectProcess(String object, String... args) {
        return SQLVoidProcess.update(getSQLStatement("delete", object), args);
    }

    /**
     * Gets an SQL process for deleting an object from the database.
     *
     * @param object the name of the object to add (e.g., "student", "course")
     * @param args   the arguments to replace in the SQL statement
     * @return a SQLVoidProcess that executes the add statement
     */
    public static SQLVoidProcess getUpdateObjectProcess(String object, String... args) {
        return SQLVoidProcess.update(getSQLStatement("update", object), args);
    }
}
