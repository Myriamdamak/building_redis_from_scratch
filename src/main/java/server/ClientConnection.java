package server;

import java.io.ByteArrayOutputStream;

/**
 * Holds per-client state across multiple read() calls. TCP doesn't guarantee
 * a whole RESP command arrives in one read, so each client needs its own
 * buffer to accumulate bytes until a full command can be parsed.
 */
public class ClientConnection {

    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public void appendData(byte[] data, int offset, int length) {
        buffer.write(data, offset, length);
    }

    public byte[] getBufferedData() {
        return buffer.toByteArray();
    }

    public void setRemaining(byte[] data, int offset, int length) {
        ByteArrayOutputStream remaining = new ByteArrayOutputStream();
        remaining.write(data, offset, length);
        this.buffer = remaining;
    }
}
