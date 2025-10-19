package de.igslandstuhl.database.utils;

import java.util.Arrays;
import org.jline.reader.*;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import de.igslandstuhl.database.server.commands.Command;

public class CommandLineUtils {
    private static LineReader reader;
    private static Terminal terminal;

    public static void setup() {
        try {
            terminal = TerminalBuilder.terminal();
            // Provide all available commands for auto-completion
            reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new StringsCompleter(Command.getAllCommandNames()))
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize terminal", e);
        }
    }

    public static synchronized String input(Object... messages) {
        String prompt = Arrays.stream(messages).map(String::valueOf).reduce("", (s1,s2) -> s1 + s2 + " ") + ">>> ";
        return reader.readLine(prompt);
    }

    public static void waitForCommandAndExec() {
        String line = input();
        String[] args = line.trim().split(" ");
        String command = args[0];
        System.out.println(Command.executeCommand(command, Arrays.copyOfRange(args, 1, args.length)));
    }
}
