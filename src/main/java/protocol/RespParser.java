package protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses raw bytes as a RESP Array of Bulk Strings, e.g.
 * *2\r\n$4\r\nECHO\r\n$3\r\nhey\r\n -> ["ECHO", "hey"]
 *
 * Returns null (rather than throwing) when the buffer doesn't yet contain
 * a complete command, so the caller knows to wait for more bytes.
 */
public class RespParser {

    public static class ParseResult {
        public final List<String> args;
        public final int nextOffset;

        public ParseResult(List<String> args, int nextOffset) {
            this.args = args;
            this.nextOffset = nextOffset;
        }
    }

    public static ParseResult parseArray(byte[] data, int offset) {
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
                return null; // not enough data yet, or malformed
            }
            pos++;

            int[] lenHolder = new int[1];
            pos = readInteger(data, pos, lenHolder);
            if (pos == -1) return null;

            int len = lenHolder[0];

            if (pos + len + 2 > data.length) {
                return null; // not enough bytes for value + trailing \r\n
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
            return -1; // haven't received the full line yet
        }
        result[0] = Integer.parseInt(new String(data, start, pos - start));
        return pos + 2;
    }
}
