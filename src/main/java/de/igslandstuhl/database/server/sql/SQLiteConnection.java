package de.igslandstuhl.database.server.sql;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

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
        executeVoidProcessSecure(this::createTables);
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