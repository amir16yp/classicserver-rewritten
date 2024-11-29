package net.classicube.api;

public interface CommandSender {
    void sendMessage(String message);

    boolean isOP();
}
