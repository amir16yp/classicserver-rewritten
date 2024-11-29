package net.classicube.api;

@FunctionalInterface
interface Command {
    String execute(CommandSender sender, String[] args);
}