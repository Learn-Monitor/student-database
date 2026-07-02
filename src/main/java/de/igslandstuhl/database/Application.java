package de.igslandstuhl.database;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.jline.reader.UserInterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.igslandstuhl.database.api.SerializationException;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.api.Topic;
import de.igslandstuhl.database.client.HTMLTemplate;
import de.igslandstuhl.database.holidays.Holiday;
import de.igslandstuhl.database.plugins.PluginLoader;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.commands.Command;
import de.igslandstuhl.database.server.webserver.WebPath;
import de.igslandstuhl.database.server.webserver.handlers.GetRequestHandler;
import de.igslandstuhl.database.server.webserver.handlers.PostRequestHandler;
import de.igslandstuhl.database.server.webserver.handlers.get.SQLRequestHandler;
import de.igslandstuhl.database.utils.CommandLineUtils;

/**
 * Represents the main application class that serves as a singleton instance.
 * This class provides methods to check if the application is running on a server.
 * It will later replace Server as main class.
 */
public final class Application {
    /** The delimiter for topics in LPT save files. */
    public static final String TOPIC_DELIMITER = "\n";
    /** The delimiter for titles in LPT save files. */
    public static final String TITLE_DELIMITER = "¶";
    /** The delimiter for task titles in LPT save files. */
    public static final String TASK_TITLE_DELIMITER = "\\|";
    /** The delimiter for tasks in LPT save files. */
    public static final String TASK_DELIMITER = "¤";

    /**
     * The application main logger
     */
    public static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
    /**
     * The logger for the student-database api
     */
    public static final Logger LOGGER_API = LoggerFactory.getLogger("de.igslandstuhl.database.api");

    private static Application instance = new Application(new String[] {"--test-environment", "true"});
    /**
     * Returns the singleton instance of the Application.
     * @return the singleton instance of the Application
     */
    public static Application getInstance() {
        return instance;
    }

    public boolean beingTested() {
        return arguments.hasKey("test-environment") && arguments.get("test-environment").equals("true") || "true".equals(System.getProperty("test.environment"));
    }

    private final boolean onServer = true;
    public boolean isOnServer() {
        return onServer;
    }

    private final Arguments arguments;
    public Arguments getArguments() {
        return arguments;
    }

    public boolean runsWebServer() {
        return !beingTested() && (!getArguments().hasKey("web-server") || getArguments().get("web-server") == "true");
    }
    public boolean suppressCmd() {
        return !beingTested() && getArguments().hasKey("suppress-cmd") && getArguments().get("suppress-cmd") == "true";
    }

    public String getOptionSafe(String key, String defaultValue) {
        if (getArguments().hasKey(key)) {
            return getArguments().get(key);
        } else if (!beingTested() && !suppressCmd()) {
            String result = CommandLineUtils.input(key, "( default:", defaultValue, ")");
            return result == "" ? defaultValue : result;
        } else {
            return defaultValue;
        }
    }

    public Application(String[] args) {
        this.arguments = new Arguments(args);
    }

    public Topic[] readFile(String file) throws SerializationException, SQLException {
        Subject subject=null;
        int grade=-1;
        List<Topic> topics = new ArrayList<>();

        try {
            String[] lines = file.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (i == 0) {
                    subject = Subject.get(line);
                } else if (i == 1) {
                    grade = Integer.parseInt(line);
                } else {
                    topics.add(Topic.fromSerialized(line, subject, grade, i - 1));
                }
            }
        } catch (Throwable t) {
            throw new SerializationException("Failed to read file", t);
        }

        Topic[] topicsArr = new Topic[topics.size()];
        return topics.toArray(topicsArr);
    }

    private static void registerBuiltinPlugins() {
        LOGGER.info("Registering built-in plugins...");
        Registry.builtinPluginRegistry().register("plugin-loader", de.igslandstuhl.database.plugins.PluginLoader.class);
    }

    public static void main(String[] args) throws Exception {
        LOGGER.info("Starting up student-database...");

        instance = new Application(args);

        registerBuiltinPlugins();
        PluginLoader.getInstance().preloadPlugins();

        if (!getInstance().suppressCmd()) {
            LOGGER.info("Setting up command line...");
            Command.registerCommands();
            CommandLineUtils.setup();
        }

        LOGGER.info("Setting up server...");

        Server.getInstance().getConnection().createTables();

        Holiday.setupCurrentSchoolYear();
        PostRequestHandler.registerHandlers();
        SQLRequestHandler.register();
        PluginLoader.getInstance().registerPlugins();

        WebPath.registerPaths();
        HTMLTemplate.registerAll();
        GetRequestHandler.getInstance().registerHandlers();

        if (getInstance().runsWebServer()) {
            LOGGER.info("Starting WebServer...");
            Server.getInstance().getWebServer().start();
        }

        PluginLoader.getInstance().enablePlugins();

        LOGGER.info("Adding shutdown hook for plugin cleanup...");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> PluginLoader.getInstance().unloadPlugins(),"Plugin cleanup thread"));

        try {
            LOGGER.info("Starting main loop...");
            while (true) {
                if (!getInstance().suppressCmd()) {
                    CommandLineUtils.waitForCommandAndExec();
                }
            }
        } catch (UserInterruptException e) {
            System.exit(0);
        } // Program exit using Ctrl+C
    }
}
