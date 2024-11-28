package classic.api;

@FunctionalInterface
interface Command {
    String execute(CommandSender sender, String[] args);
}