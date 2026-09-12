import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class Main {
    public static void main(String[] args) {
        System.out.println("Logs from your program will appear here!");

        int port = 6379;

        try {
            // 1. Set up a non-blocking server socket channel
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);

            // 2. Create the Selector
            Selector selector = Selector.open();

            // 3. Register the server channel, interested in "accept" events
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Server listening on port " + port);

            // 4. The event loop
            while (true) {
                // Blocks until at least one channel is ready for an event
                selector.select();

                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove(); // must remove manually, selector doesn't do it for you

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
            }

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static void handleAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            // Register this new client, interested in "read" events
            clientChannel.register(selector, SelectionKey.OP_READ);
            System.out.println("Accepted new connection: " + clientChannel.getRemoteAddress());
        }
    }

    private static void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        int bytesRead;
        try {
            bytesRead = clientChannel.read(buffer);
        } catch (IOException e) {
            // Connection reset by client, treat like a close
            key.cancel();
            clientChannel.close();
            return;
        }

        if (bytesRead == -1) {
            // Client closed the connection
            key.cancel();
            clientChannel.close();
            return;
        }

        if (bytesRead > 0) {
            // Hardcoded response for now, regardless of what was sent
            ByteBuffer response = ByteBuffer.wrap("+PONG\r\n".getBytes());
            clientChannel.write(response);
        }
    }
}