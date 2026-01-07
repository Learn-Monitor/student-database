package de.igslandstuhl.database;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import de.igslandstuhl.database.api.SerializationException;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.api.Topic;
import de.igslandstuhl.database.api.modules.WebModule;
import de.igslandstuhl.database.holidays.Holiday;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.commands.Command;
import de.igslandstuhl.database.server.webserver.WebPath;
import de.igslandstuhl.database.server.webserver.handlers.GetRequestHandler;
import de.igslandstuhl.database.server.webserver.handlers.PostRequestHandler;
import de.igslandstuhl.database.utils.CommandLineUtils;

/**
 * Represents the main application class that serves as a singleton instance.
 * This class provides methods to check if the application is running on a server.
 * It will later replace Server as main class.
 */
public final class Application {
    public static final String TOPIC_DELIMITER = "\n";
    public static final String TITLE_DELIMITER = "¶";
    public static final String TASK_TITLE_DELIMITER = "\\|";
    public static final String TASK_DELIMITER = "¤";
    private static Application instance = new Application(new String[] {"--test-environment", "true"});
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
            t.printStackTrace();
            throw new SerializationException("Failed to read file", t);
        }

        Topic[] topicsArr = new Topic[topics.size()];
        return topics.toArray(topicsArr);
    }

    public static void main(String[] args) throws Exception {
        instance = new Application(args);
        
        if (!getInstance().suppressCmd()) {
            Command.registerCommands();
            CommandLineUtils.setup();
        }
        
        Server.getInstance().getConnection().createTables();

        Holiday.setupCurrentSchoolYear();
        PostRequestHandler.registerHandlers();
        WebModule.registerModules();

        WebPath.registerPaths();
        GetRequestHandler.getInstance().registerHandlers();

        if (getInstance().runsWebServer()) {
            Server.getInstance().getWebServer().start();
        }

        while (true) {
            if (!getInstance().suppressCmd()) {
                CommandLineUtils.waitForCommandAndExec();
            }
        }
    }
}
