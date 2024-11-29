package net.classicube.api;

public class RegisteredCommand {
    private final Command command;
    private final boolean requiresOp;

    public RegisteredCommand(Command command, boolean requiresOp) {
        this.command = command;
        this.requiresOp = requiresOp;
    }

    public Command getCommand() {
        return command;
    }

    public boolean isRequiresOp() {
        return requiresOp;
    }
}