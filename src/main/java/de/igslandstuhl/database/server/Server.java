package de.igslandstuhl.database.server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.codec.digest.DigestUtils;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.api.Room;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Student;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.api.Teacher;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.resources.ResourceManager;
import de.igslandstuhl.database.server.sql.SQLHelper;
import de.igslandstuhl.database.server.sql.SQLiteConnection;

/**
 * Represents the main server class that handles all incoming requests and manages the database connection.
 * This class is a singleton, ensuring that only one instance of the server exists throughout the application.
 * It provides methods to process SQL requests, manage user authentication, and retrieve SQL resources.
 * The server also initializes a web server to handle HTTP requests.
 * This class implements AutoCloseable to ensure proper resource management, allowing the server to be closed gracefully.
 * This is also the main entry point for the server application. but will be replaced by the de.igslandstuhl.database.Application class in the future.
 */
public final class Server implements AutoCloseable {
    // Singleton instance of the Server class
    private static final Server instance = new Server();
    /**
     * Returns the singleton instance of the Server class.
     * This method provides access to the server instance, ensuring that only one instance is used throughout the application.
     *
     * @return The singleton instance of the Server class.
     */
    public static Server getInstance() {
        return instance;
    }
    /**
     * The SQLiteConnection instance used to interact with the database.
     * This connection is used to execute SQL queries and manage database transactions.
     * It is synchronized to ensure thread safety when processing requests.
     */
    private final SQLiteConnection connection;
    /**
     * Returns the SQLiteConnection instance used by the server.
     * This method provides access to the database connection, allowing for SQL queries to be executed.
     *
     * @return The SQLiteConnection instance used by the server.
     */
    public SQLiteConnection getConnection() {
        return connection;
    }
    /**
     * The WebServer instance that handles incoming HTTP requests.
     * This server listens for requests on a specified port and processes them accordingly.
     * It is initialized with a keystore for secure connections.
     */
    private final WebServer webServer;
    /**
     * Returns the WebServer instance used by the server.
     * This method provides access to the web server.
     *
     * @return The WebServer instance used by the server.
     */
    public WebServer getWebServer() {
        return webServer;
    }
    /**
     * The resource manager for this server
     */
    private final ResourceManager resourceManager = new ResourceManager();
    /**
     * Returns the resource manager used by this server
     * @return the resource manager
     */
    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    /**
     * Private constructor to initialize the server instance.
     * This constructor sets up the database connection and initializes the web server.
     * It throws an IllegalStateException if the server fails to start.
     */
    private Server() {
        try {
            connection = new SQLiteConnection(Application.getInstance().getOptionSafe("database", Application.getInstance().beingTested() ? "test-server-" + System.currentTimeMillis() : "database"));
            String keystorePath = Application.getInstance().runsWebServer() ? Application.getInstance().getOptionSafe("keystore", "keys/web/keystore.jks") : null;
            String keystorePassword = Application.getInstance().runsWebServer() ? Application.getInstance().getOptionSafe("keystore-password", "changeit") : null;
            int port = 443;
            webServer = Application.getInstance().runsWebServer() ? new WebServer(port, keystorePath, keystorePassword) : new WebServer();
        } catch (Exception e) {
            throw new IllegalStateException("Server failed on start", e);
        }
    }

    /**
     * Processes a single SQL request and returns the result.
     * This method executes a SQL query and applies a function to the result set, returning the processed output.
     * It is synchronized to ensure thread safety when accessing the database connection.
     *
     * @param output The function to apply to the result set.
     * @param request The SQL query to execute.
     * @param sqlFields The fields to retrieve from the result set.
     * @param args Additional arguments for the SQL query.
     * @return The processed output of type T, or null if no results are found.
     * @throws SQLException If an error occurs while executing the SQL query.
     */
    public <T> T processSingleRequest(Function<String[],T> output, String request, String[] sqlFields, String... args) throws SQLException {
        try {
            ResultSet result = connection.executeProcess(SQLHelper.getQueryProcess(request, args));
            if (result.next()){
                List<String> results = new ArrayList<>();
                for (String columnLabel : sqlFields) {
                    results.add(result.getString(columnLabel));
                }
                connection.closePendingStatement();
                String[] resultArr = new String[results.size()];
                return output.apply(results.toArray(resultArr));
            } else {
                return null;
            }
        } finally {
            connection.closePendingStatement();
        }
    }
    /**
     * Processes a SQL request and applies a callback to each row of the result set.
     * This method executes a SQL query and iterates through the result set, applying the callback to each row.
     * It creates a new Thread to ensure the callbacks do not interrupt the database query.
     *
     * @param callback The callback function to apply to each row of the result set.
     * @param request The SQL query to execute.
     * @param sqlFields The fields to retrieve from the result set.
     * @param args Additional arguments for the SQL query.
     * @throws SQLException If an error occurs while executing the SQL query.
     */
    public void processRequest(Consumer<String[]> callback, String request, String[] sqlFields, String... args) throws SQLException {
        try {
            ResultSet result = connection.executeProcess(SQLHelper.getQueryProcess(request, args));
            Thread subroutine = new Thread(() -> {
                try {
                    while (result.next()) {
                        List<String> results = new LinkedList<>();
                        for (String columnLabel : sqlFields) {
                            results.add(result.getString(columnLabel));
                        }
                        String[] resultArr = new String[results.size()];
                        callback.accept(results.toArray(resultArr));
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    throw new IllegalStateException(e);
                }
            });
            subroutine.start();
            try {
                subroutine.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new IllegalStateException(e);
            }
        } finally {
            connection.closePendingStatement();
        }
    }
    public String[][] processRequest(String request, String[] sqlFields, String... args) throws SQLException {
        List<String[]> rows = new LinkedList<>();
        processRequest(row -> rows.add(row), request, sqlFields, args);
        String[][] rowsArr = new String[rows.size()][];
        return rows.toArray(rowsArr);
    }

    /**
     * Validates a user by checking the provided username and password against the stored user data.
     * This method retrieves the user from the database and compares the provided password with the stored password hash.
     *
     * @param username The username of the user to validate.
     * @param password The password of the user to validate.
     * @return true if the user is valid, false otherwise.
     */
    public boolean isValidUser(String username, String password) {
        User user = User.getUser(username);
        if (user != null && user.getPasswordHash().equals(DigestUtils.sha1Hex(password))) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Retrieves an SQL resource for the specified user and resource name.
     * This method checks the resource name and returns the corresponding data in JSON format.
     *
     * @param username The username of the user requesting the resource.
     * @param resource The name of the resource to retrieve.
     * @return The JSON representation of the requested resource, or null if not found.
     */
    public String getSQLResource(String username, String resource) {
        resource = resource.intern();
        if (resource.equals("mydata")) {
            User user = User.getUser(username);
            return user.toJSON();
        } else if (resource.equals("rooms")) {
            return new HashSet<>(Room.getRooms().values()).toString();
        } else if (resource.equals("mysubjects")) {
            User user = User.getUser(username);
            if (user instanceof Student student) {
                return student.getSchoolClass().getSubjects().toString();
            } else if (user instanceof Teacher teacher) {
                return teacher.getSubjects().toString();
            } else {
                return null;
            }
        } else if (resource.equals("myclasses")) {
            User user = User.getUser(username);
            if (user instanceof Teacher teacher) {
                Set<Integer> classIDs = teacher.getClassIds();
                Set<String> classes = new HashSet<>();
                for (Integer classID : classIDs) {
                    classes.add("{\"classId\": " + classID + ", \"name\": \"" + SchoolClass.get(classID).getLabel() + "\"}");
                }
                return classes.toString();
            } else {
                return null;
            }
        } else if (resource.equals("teachers")) {
            return new HashSet<>(Teacher.getAll()).toString();
        } else if (resource.equals("students")) {
            return new HashSet<>(Student.getAll()).toString();
        } else if (resource.equals("subjects")) {
            return new HashSet<>(Subject.getAll()).toString();
        } else if (resource.equals("classes")) {
            return new HashSet<>(SchoolClass.getAll()).toString();
        } else if (resource.equals("all-student-results")) {
            return Student.getAllResultsCSV();
        } else {
            return null;
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
        webServer.stop();
    }
}
