package net.classicube.api;

import java.io.IOException;

@FunctionalInterface
public interface Command {
    String execute(CommandSender sender, String[] args) throws IOException;
}