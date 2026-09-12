import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.LinkedList;

public class Main {

    // Shared in-memory store for SET/GET
    private static final Map<String, CacheEntry> cache = new HashMap<>();
    private static Map<String, List<String>> lists = new HashMap<>();
    public static void main(String[] args) {
        System.out.println("Logs from your program will appear here!");

        int port = 6379;

        try {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);

            Selector selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Server listening on port " + port);

            while (true) {
                selector.select();

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
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            clientKey.attach(new ByteArrayOutputStream());
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
            ByteArrayOutputStream connBuffer = (ByteArrayOutputStream) key.attachment();
            connBuffer.write(buffer.array(), 0, bytesRead);

            byte[] data = connBuffer.toByteArray();
            int offset = 0;

            while (offset < data.length) {
                RespParser.ParseResult result = RespParser.parseArray(data, offset);
                if (result == null) {
                    break;
                }

                List<String> commandArgs = result.args;
                offset = result.nextOffset;

                String response = dispatchCommand(commandArgs);
                if (response != null) {
                    clientChannel.write(ByteBuffer.wrap(response.getBytes()));
                }
            }

            ByteArrayOutputStream remaining = new ByteArrayOutputStream();
            remaining.write(data, offset, data.length - offset);
            key.attach(remaining);
        }
    }

    private static String dispatchCommand(List<String> args) {
        if (args.isEmpty()) return null;

        String command = args.get(0).toUpperCase();

        switch (command) {
            case "PING":
                return "+PONG\r\n";

            case "ECHO": {
                if (args.size() < 2) {
                    return "-ERR wrong number of arguments for 'echo' command\r\n";
                }
                String arg = args.get(1);
                return "$" + arg.length() + "\r\n" + arg + "\r\n";
            }

            case "SET": {
                if (args.size() < 3) {
                    return "-ERR wrong number of arguments for 'set' command\r\n";
                }
                String key = args.get(1);
                String value = args.get(2);

                Long expiryAt = null;

                // Parse optional arguments (PX, EX, etc.) starting at index 3
                for (int i = 3; i < args.size(); i++) {
                    String option = args.get(i).toUpperCase();

                    if (option.equals("PX")) {
                        if (i + 1 >= args.size()) {
                            return "-ERR syntax error\r\n";
                        }
                        long millis;
                        try {
                            millis = Long.parseLong(args.get(i + 1));
                        } catch (NumberFormatException e) {
                            return "-ERR value is not an integer or out of range\r\n";
                        }
                        expiryAt = System.currentTimeMillis() + millis;
                        i++;
                    } else if (option.equals("EX")) {
                        if (i + 1 >= args.size()) {
                            return "-ERR syntax error\r\n";
                        }
                        long seconds;
                        try {
                            seconds = Long.parseLong(args.get(i + 1));
                        } catch (NumberFormatException e) {
                            return "-ERR value is not an integer or out of range\r\n";
                        }
                        expiryAt = System.currentTimeMillis() + (seconds * 1000);
                        i++;
                    }
                    // Unknown options are silently ignored for now; extend as needed
                }

                cache.put(key, new CacheEntry(value, expiryAt));
                return "+OK\r\n";
            }

            case "GET": {
                if (args.size() < 2) {
                    return "-ERR wrong number of arguments for 'get' command\r\n";
                }
                String key = args.get(1);
                CacheEntry entry = cache.get(key);

                if (entry == null) {
                    return "$-1\r\n";
                }

                if (entry.isExpired()) {
                    cache.remove(key); // lazy expiration cleanup
                    return "$-1\r\n";
                }

                String value = entry.value;
                return "$" + value.length() + "\r\n" + value + "\r\n";
            }
            case "RPUSH": {
                if (args.size() < 3) {
                    return "-ERR wrong number of arguments for 'rpush' command\r\n";
                }
                String key = args.get(1);

                List<String> list = lists.computeIfAbsent(key, k -> new LinkedList<>());

                for (int i = 2; i < args.size(); i++) {
                    list.add(args.get(i));
                }

                return ":" + list.size() + "\r\n";
            }

            case "LPUSH": {
                if (args.size() < 3) {
                    return "-ERR wrong number of arguments for 'lpush' command\r\n";
                }
                String key = args.get(1);

                List<String> list = lists.computeIfAbsent(key, k -> new LinkedList<>());

                for (int i = args.size() - 1; i >= 2; i--) {
                    list.add(args.get(i));
                }

                return ":" + list.size() + "\r\n";
            }

            case "LLEN": {
                if (args.size() < 2) {
                    return "-ERR wrong number of arguments for 'llen' command\r\n";
                }

                String key = args.get(1);
                List<String> list = lists.get(key);

                if (list == null) {
                    return ":0\r\n";
                } else {
                    return ":" + list.size() + "\r\n";
                }
            }

            case "LPOP": {
                if (args.size() < 2) {
                    return "-ERR wrong number of arguments for 'lpop' command\r\n";
                }

                String key = args.get(1);
                List<String> list = lists.get(key);

                if (list == null || list.isEmpty()) {
                    return "$-1\r\n";
                } else {
                    String popped = list.remove(0);
                    return "$" + popped.length() + "\r\n" + popped + "\r\n";
                }

            }
            case "LRANGE": {
                if (args.size() < 4) {
                    return "-ERR wrong number of arguments for 'lrange' command\r\n";
                }

                String key = args.get(1);
                List<String> list = lists.get(key);

                if (list == null) {
                    return ":0\r\n";
                }

                int start;
                int end;
                try {
                    start = Integer.parseInt(args.get(2));
                    end = Integer.parseInt(args.get(3));
                } catch (NumberFormatException e) {
                    return "-ERR value is not an integer or out of range\r\n";
                }

                int size = list.size();

                if (start < 0) start = Math.max(size + start, 0);
                if (end < 0) end = size + end;
                if (end >= size) end = size - 1;

                if (start > end || start >= size || size == 0) {
                    return "*0\r\n";
                }

                StringBuilder sb = new StringBuilder();
                int count = end - start + 1;
                sb.append("*").append(count).append("\r\n");

                for (int i = start; i <= end; i++) {
                    String item = list.get(i);
                    sb.append("$").append(item.length()).append("\r\n");
                    sb.append(item).append("\r\n");
                }

                return sb.toString();
            }


            default:
                return "-ERR unknown command '" + command + "'\r\n";
        }
    }
}

class CacheEntry {
    String value;
    Long expiryAt; // epoch millis; null = no expiry

    CacheEntry(String value, Long expiryAt) {
        this.value = value;
        this.expiryAt = expiryAt;
    }

    boolean isExpired() {
        return expiryAt != null && System.currentTimeMillis() >= expiryAt;
    }
}

class RespParser {

    static class ParseResult {
        List<String> args;
        int nextOffset;

        ParseResult(List<String> args, int nextOffset) {
            this.args = args;
            this.nextOffset = nextOffset;
        }
    }

    static ParseResult parseArray(byte[] data, int offset) {
        if (offset >= data.length || data[offset] != '*') {
            return null;
        }

        int pos = offset + 1;
        int[] numElementsHolder = new int[1];
        pos = readInteger(data, pos, numElementsHolder);
        if (pos == -1) return null;

        int numElements = numElementsHolder[0];
        List<String> args = new ArrayList<>();

        for (int i = 0; i < numElements; i++) {
            if (pos >= data.length || data[pos] != '$') {
                return null;
            }
            pos++;

            int[] lenHolder = new int[1];
            pos = readInteger(data, pos, lenHolder);
            if (pos == -1) return null;

            int len = lenHolder[0];

            if (pos + len + 2 > data.length) {
                return null;
            }

            String value = new String(data, pos, len);
            args.add(value);
            pos += len + 2;
        }

        return new ParseResult(args, pos);
    }

    private static int readInteger(byte[] data, int pos, int[] result) {
        int start = pos;
        while (pos < data.length && data[pos] != '\r') {
            pos++;
        }
        if (pos + 1 >= data.length || data[pos] != '\r' || data[pos + 1] != '\n') {
            return -1;
        }
        result[0] = Integer.parseInt(new String(data, start, pos - start));
        return pos + 2;
    }
}