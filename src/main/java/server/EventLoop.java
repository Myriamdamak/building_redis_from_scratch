package server;

import command.CommandContext;
import command.CommandDispatcher;
import protocol.RespParser;
import store.ListStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;

/**
 * Owns the single-threaded Selector loop: accepts new connections, reads
 * bytes from existing ones, hands complete commands to the
 * CommandDispatcher, and writes the resulting reply back to the client.
 *
 * Also periodically checks for BLPOP clients whose wait has timed out -
 * this is why select() is called with a fixed poll interval instead of
 * blocking indefinitely; otherwise a timeout could never fire without
 * some unrelated network activity waking the loop up first.
 */
public class EventLoop {

    // How often the loop wakes up on its own (even with zero I/O activity)
    // to check for expired BLPOP waiters. Smaller = more precise timeouts,
    // at the cost of slightly more CPU wakeups. 100ms is a reasonable default.
    private static final long POLL_INTERVAL_MILLIS = 100;

    private final int port;
    private final CommandDispatcher dispatcher;
    private final ListStore listStore;

    public EventLoop(int port, CommandDispatcher dispatcher, ListStore listStore) {
        this.port = port;
        this.dispatcher = dispatcher;
        this.listStore = listStore;
    }

    public void start() throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);

        Selector selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server listening on port " + port);

        while (true) {
            selector.select(POLL_INTERVAL_MILLIS);

            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                try {
                    if (key.isAcceptable()) {
                        handleAccept(key, selector);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                } catch (IOException e) {
                    System.out.println("IOException on key: " + e.getMessage());
                    key.cancel();
                    key.channel().close();
                }
            }

            // Runs whether or not any socket had activity this tick -
            // this is what lets BLPOP timeouts fire on schedule.
            listStore.checkExpiredWaiters();
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            clientKey.attach(new ClientConnection());
            System.out.println("Accepted new connection: " + clientChannel.getRemoteAddress());
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        int bytesRead;
        try {
            bytesRead = clientChannel.read(buffer);
        } catch (IOException e) {
            key.cancel();
            clientChannel.close();
            return;
        }

        if (bytesRead == -1) {
            key.cancel();
            clientChannel.close();
            return;
        }

        if (bytesRead > 0) {
            ClientConnection connection = (ClientConnection) key.attachment();
            connection.appendData(buffer.array(), 0, bytesRead);

            byte[] data = connection.getBufferedData();
            int offset = 0;

            CommandContext context = new CommandContext(clientChannel);

            // Parse and handle as many complete commands as are buffered
            while (offset < data.length) {
                RespParser.ParseResult result = RespParser.parseArray(data, offset);
                if (result == null) {
                    break; // incomplete command, wait for more bytes
                }

                List<String> commandArgs = result.args;
                offset = result.nextOffset;

                String response = dispatcher.dispatch(commandArgs, context);
                if (response != null) {
                    clientChannel.write(ByteBuffer.wrap(response.getBytes()));
                }
                // response == null means the command (e.g. BLPOP) deferred
                // its reply - nothing to write here, it'll come later.
            }

            // Keep only the unconsumed leftover bytes for next time
            connection.setRemaining(data, offset, data.length - offset);
        }
    }
}
