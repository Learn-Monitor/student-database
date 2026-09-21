package de.igslandstuhl.database.server.sql;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.utils.TrackingReadWriteLock;

/**
 * Represents a connection to an SQLite database.
 */
public class SQLiteConnection implements AutoCloseable, PreparedStatementSupplier {
    /**
     * The URL of the SQLite database.
     * It is constructed as "jdbc:sqlite:" + url + ".db".
     */
    private final String url;

    private final ThreadLocal<Connection> connectionSupplier;
    /**
     * Returns the <code>java.sql.Connection</code> associated with this <code>SQLiteConnection</code>.
     * @return the <code>Connection</code> object
     */
    public Connection getSQLConnection() {
        return connectionSupplier.get();
    }
    /**
     * Current statement in this thread that need to be closed when the connection is closed.
     */
    private ThreadLocal<PreparedStatement> pendingStatement = new ThreadLocal<>();

    private final TrackingReadWriteLock lock = new TrackingReadWriteLock();

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    /**
     * Creates the necessary tables in the database by executing SQL scripts.
     * This method reads SQL files matching the pattern "./tables/*.sql" (regex: .*tables.+\\.sql) and executes their content.
     * @param supplier the Statement object used to execute the SQL commands
     * @throws SQLException if an SQL error occurs during table creation
     */
    private void createTables(PreparedStatementSupplier supplier) throws SQLException {
        lock.writeLock().lock();
        try {
            for (BufferedReader in : Server.getInstance().getResourceManager().openResourcesAsReader(Pattern.compile(".*tables.+\\.sql"))) {
                try (in) {
                    String request = Server.getInstance().getResourceManager().readResourceCompletely(in);
                    supplier.executeUpdate(request);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    /**
     * Applies schema migrations to the database by executing SQL scripts.
     * This method reads SQL files matching the pattern "./migrations/*.sql" (regex: .*migrations.+\.sql) and executes their content.
     * The final legacy migration added {@code students.active}; when that column exists, migrations
     * 001-020 must not be replayed because some of them rebuild tables destructively.
     * @param supplier the Statement object used to execute the SQL commands
     * @throws SQLException if an SQL error occurs during migration that is not an "already applied" error
     */
    private void migrateTables(PreparedStatementSupplier supplier) throws SQLException {
        lock.writeLock().lock();
        try {
            if (hasColumn("students", "active")) {
                LOGGER.info("Legacy database migrations 001-020 are already complete (students.active exists); skipping replay.");
                return;
            }
            for (BufferedReader in : Server.getInstance().getResourceManager().openResourcesAsReader(Pattern.compile(".*migrations.+\\.sql"))) {
                try (in) {
                    String request = Server.getInstance().getResourceManager().readResourceCompletely(in);
                    for (String statement : splitSqlScript(request)) {
                        try {
                            supplier.executeUpdate(statement);
                        } catch (SQLException e) {
                            if (e.getMessage() != null && (
                                    e.getMessage().toLowerCase().contains("duplicate column name")
                                    || e.getMessage().toLowerCase().contains("no such column")
                                    || e.getMessage().toLowerCase().contains("no such table")
                                    || e.getMessage().toLowerCase().contains("already exists")
                                )) {
                                LOGGER.debug("Skipping already applied migration statement: {}", statement);
                            } else {
                                throw e;
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    boolean hasColumn(String table, String column) throws SQLException {
        try (PreparedStatement statement = getSQLConnection().prepareStatement(
                "SELECT 1 FROM pragma_table_info(?) WHERE name = ? LIMIT 1")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
    static List<String> splitSqlScript(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false, doubleQuoted = false, lineComment = false;
        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';
            if (lineComment) {
                current.append(ch);
                if (ch == '\n' || ch == '\r') lineComment = false;
                continue;
            }
            if (!singleQuoted && !doubleQuoted && ch == '-' && next == '-') {
                lineComment = true;
                current.append(ch);
                continue;
            }
            if (ch == '\'' && !doubleQuoted) {
                current.append(ch);
                if (singleQuoted && next == '\'') {
                    current.append(next);
                    i++;
                } else {
                    singleQuoted = !singleQuoted;
                }
                continue;
            }
            if (ch == '"' && !singleQuoted) {
                current.append(ch);
                if (doubleQuoted && next == '"') {
                    current.append(next);
                    i++;
                } else {
                    doubleQuoted = !doubleQuoted;
                }
                continue;
            }
            if (ch == ';' && !singleQuoted && !doubleQuoted) {
                addStatement(statements, current);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        addStatement(statements, current);
        return statements;
    }
    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().strip();
        if (!statement.isEmpty()) statements.add(statement);
    }
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        if (pendingStatement.get() != null && !pendingStatement.get().isClosed()) throw new SQLMultipleAccessesException("Multiple statements created at one time in one thread - use another Thread for callbacks etc.");
        PreparedStatement stmt = getSQLConnection().prepareStatement(sql);
        pendingStatement.set(stmt);
        return stmt;
    }
    /**
     * Executes a SQL statement securely, ensuring that the statement is closed after execution.
     * @param statement the PreparedStatement to execute
     * @return a ResultSet containing the results of the query
     * @throws SQLException if an SQL error occurs during execution
     */
    public ResultSet executeStatementQuerySecure(PreparedStatement statement) throws SQLException {
        lock.readLock().lock();
        try (statement) {
            return statement.executeQuery();
        } finally {
            lock.readLock().unlock();
        }
    }
    /**
     * Executes a SQL statement that does not return a result set.
     * @param sql the SQL command to execute
     * @throws SQLException if an SQL error occurs during execution
     */
    public void executeVoidProcessSecure(String sql) throws SQLException {
        lock.writeLock().lock();
        try (Statement stmt = getSQLConnection().createStatement()) {
            stmt.execute(sql);
        } finally {
            lock.writeLock().unlock();
        }
    }
    /**
     * Executes a SQL void process securely, ensuring that the statement is closed after execution.
     * @param p the SQLVoidProcess to execute
     * @throws SQLException if an SQL error occurs during execution
     */
    public void executeVoidProcessSecure(SQLVoidProcess p) throws SQLException {
        lock.writeLock().lock();
        try {
            p.execute(this);
        } finally {
            closePendingStatement();
            lock.writeLock().unlock();
        }
    }
    /**
     * Executes a SQL process that returns a ResultSet.
     * @param p the SQLProcess to execute
     * @return a ResultSet containing the results of the query
     * @throws SQLException if an SQL error occurs during execution
     */
    public ResultSet executeProcess(SQLProcess p) throws SQLException {
        if (p instanceof SQLQueryProcess qp) return executeProcess(qp);
        lock.writeLock().lock();
        try {
            return p.execute(this);
        } finally {
            lock.writeLock().unlock();
        }
    }
    /**
     * Executes a SQL process that returns a ResultSet.
     * @param p the SQLProcess to execute
     * @return a ResultSet containing the results of the query
     * @throws SQLException if an SQL error occurs during execution
     */
    public ResultSet executeProcess(SQLQueryProcess p) throws SQLException {
        lock.readLock().lock();
        try {
            return p.execute(this);
        } finally {
            lock.readLock().unlock();
        }
    }
    /**
     * Closes all pending statements that have been created during the lifetime of this connection.
     * This method should be called before closing the connection to ensure that all resources are released.
     * @throws SQLException if an error occurs while closing the statements
     */
    public void closeAllPendingStatements() throws SQLException {
        pendingStatement.remove();
    }
    /**
     * Closes the pending statement in the current thread
     * @throws SQLException
     */
    public void closePendingStatement() throws SQLException {
        pendingStatement.get().close();
    }
    /**
     * Creates the necessary tables in the database by executing SQL scripts.
     * This method reads SQL files matching the pattern "./tables/*.sql" (regex: .*tables.+\\.sql) and executes their content.
     * @throws SQLException if an SQL error occurs during table creation
     */
    public void createTables() throws SQLException {
        LOGGER.debug("Creating Database Tables...");
        executeVoidProcessSecure(this::createTables);
    }
    /**
     * Applies schema migrations to the database by executing SQL scripts.
     * This method reads SQL files matching the pattern "./migrations/*.sql" (regex: .*migrations.+\.sql) and executes their content.
     * @throws SQLException if an SQL error occurs during migration
     */
    public void migrateTables() throws SQLException {
        LOGGER.debug("Applying database migrations...");
        executeVoidProcessSecure(this::migrateTables);
    }
    
    /**
     * Constructs a new SQLiteConnection with the specified database URL.
     * The URL is prefixed with "jdbc:sqlite:" and suffixed with ".db".
     * @param url the name of the database file (without extension)
     * @throws SQLException if an error occurs while establishing the connection
     */
    public SQLiteConnection(String url) throws SQLException {
        this.url = "jdbc:sqlite:" + url + ".db";
        this.connectionSupplier = ThreadLocal.withInitial(() -> {
            try {
                return DriverManager.getConnection(this.url);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }
    @FunctionalInterface
    public interface Transaction<T> { T run(Connection connection) throws SQLException; }

    /** Serialize read-check-write with all core writers and SQLite writers in other processes. */
    public <T> T writeTransaction(Transaction<T> work) throws SQLException {
        return writeTransaction(work, result -> {});
    }

    public <T> T writeTransaction(Transaction<T> work, java.util.function.Consumer<T> committed) throws SQLException {
        lock.writeLock().lock();
        try {
            Connection c = getSQLConnection();
            T result;
            try (Statement statement = c.createStatement()) {
                statement.execute("BEGIN IMMEDIATE");
                try {
                    result = work.run(c);
                    statement.execute("COMMIT");
                } catch (SQLException | RuntimeException e) {
                    try { statement.execute("ROLLBACK"); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                    throw e;
                }
            }
            committed.accept(result);
            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() throws SQLException {
        lock.interruptAll();
        closeAllPendingStatements();
        connectionSupplier.remove();
    }
    public static void main(String[] args) throws SQLException {
        String url = "lernjobs"; // Datenbank-Datei im Projektverzeichnis

        try (SQLiteConnection c = new SQLiteConnection(url)) {
            c.createTables();
        }

    }
}
