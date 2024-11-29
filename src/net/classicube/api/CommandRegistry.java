package net.classicube.api;

import java.util.HashMap;
import java.util.Map;

public class CommandRegistry {

    private final Map<String, RegisteredCommand> commands = new HashMap<>();

    public void registerCommand(String name, boolean requiresOp, Command command) {
        commands.put(name.toLowerCase(), new RegisteredCommand(command, requiresOp));
    }

    public void executeCommand(CommandSender sender, String input) {
        String[] split = input.split("\\s+", 2);
        String commandName = split[0].toLowerCase();
        String[] args = split.length > 1 ? split[1].split("\\s+") : new String[0];

        RegisteredCommand command = commands.get(commandName);
        if (command == null) {
            sender.sendMessage("Unknown command. Type 'help' for available commands.");
            return;
        }

        if (command.isRequiresOp() && !sender.isOP()) {
            sender.sendMessage("You don't have permission to use this command.");
            return;
        }

        try {
            sender.sendMessage(command.getCommand().execute(sender, args));
        } catch (Exception e) {
            sender.sendMessage("Error executing command: " + e.getMessage());
        }
    }

    public Map<String, RegisteredCommand> getCommands() {
        return commands;
    }
}