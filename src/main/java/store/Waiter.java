package store;

import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * A client currently blocked inside a BLPOP call. It's registered against
 * every key it's waiting on; whichever key gets pushed to first "wins" and
 * the waiter is removed from all the others.
 */
public class Waiter {

    public final List<String> keys;
    public final SocketChannel channel;
    public final Long deadlineMillis; // null = block forever (timeout of 0)

    private boolean served = false;

    public Waiter(List<String> keys, SocketChannel channel, Long deadlineMillis) {
        this.keys = keys;
        this.channel = channel;
        this.deadlineMillis = deadlineMillis;
    }

    public boolean isExpired() {
        return deadlineMillis != null && System.currentTimeMillis() >= deadlineMillis;
    }

    public boolean isServed() {
        return served;
    }

    public void markServed() {
        served = true;
    }
}
