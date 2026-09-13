import server.EventLoop;
import command.CommandDispatcher;
import store.StringStore;
import store.ListStore;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        System.out.println("Logs from your program will appear here!");

        int port = 6379;

        // Shared data stores, passed into the dispatcher so every command
        // reads/writes the same underlying maps.
        StringStore stringStore = new StringStore();
        ListStore listStore = new ListStore();

        CommandDispatcher dispatcher = new CommandDispatcher(stringStore, listStore);

        EventLoop eventLoop = new EventLoop(port, dispatcher, listStore);

        try {
            eventLoop.start();
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
}
