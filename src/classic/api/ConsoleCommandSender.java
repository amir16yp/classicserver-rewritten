package classic.api;

public class ConsoleCommandSender implements CommandSender{
    @Override
    public void sendMessage(String message) {
        System.out.println("(Server Console) "+message);
    }

    @Override
    public boolean isOP() {
        return true;
    }
}
