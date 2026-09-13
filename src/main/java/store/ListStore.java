package store;

import protocol.RespEncoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Backing store for list commands (RPUSH, LPUSH, LLEN, LPOP, LRANGE, BLPOP).
 * Uses LinkedList internally so push/pop at the front are O(1).
 *
 * Also owns the BLPOP waiter registry: clients blocked waiting for a key
 * to receive data are tracked here, and get served directly the moment
 * RPUSH/LPUSH adds something to a key they're watching.
 */
public class ListStore {

    private final Map<String, List<String>> lists = new HashMap<>();

    // One FIFO queue of waiters per key being watched
    private final Map<String, Deque<Waiter>> waitersByKey = new HashMap<>();

    // Every waiter currently outstanding, so we can scan for timeouts each loop tick
    private final List<Waiter> allWaiters = new ArrayList<>();

    public List<String> getOrCreate(String key) {
        return lists.computeIfAbsent(key, k -> new LinkedList<>());
    }

    public List<String> get(String key) {
        return lists.get(key);
    }

    /** Registers a client as blocked on BLPOP, waiting on any of these keys. */
    public void addWaiter(Waiter waiter) {
        for (String key : waiter.keys) {
            waitersByKey.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(waiter);
        }
        allWaiters.add(waiter);
    }

    /**
     * Call this after RPUSH/LPUSH adds elements to `key`. If a client is
     * blocked on BLPOP for this key, immediately pop one element per
     * waiting client and deliver it directly to them, instead of leaving
     * it in the list for a later LPOP/BLPOP call to find.
     */
    public void serveWaitersIfAny(String key) {
        Deque<Waiter> queue = waitersByKey.get(key);
        if (queue == null) {
            return;
        }

        List<String> list = lists.get(key);

        while (!queue.isEmpty() && list != null && !list.isEmpty()) {
            Waiter waiter = queue.pollFirst();

            if (waiter.isServed() || waiter.isExpired()) {
                continue; // already handled elsewhere (timed out, or served via another key)
            }

            String value = list.remove(0);
            waiter.markServed();
            removeFromAllQueues(waiter);

            List<String> encoded = new ArrayList<>();
            encoded.add(RespEncoder.bulkString(key));
            encoded.add(RespEncoder.bulkString(value));
            writeToChannel(waiter.channel, RespEncoder.array(encoded));
        }
    }

    /**
     * Call this once per event loop tick. Any waiter whose deadline has
     * passed gets a nil-array reply and is removed from the registry.
     */
    public void checkExpiredWaiters() {
        Iterator<Waiter> it = allWaiters.iterator();
        while (it.hasNext()) {
            Waiter waiter = it.next();

            if (waiter.isServed()) {
                it.remove();
                continue;
            }

            if (waiter.isExpired()) {
                waiter.markServed();
                removeFromAllQueues(waiter);
                writeToChannel(waiter.channel, RespEncoder.nullArray());
                it.remove();
            }
        }
    }

    private void removeFromAllQueues(Waiter waiter) {
        for (String key : waiter.keys) {
            Deque<Waiter> queue = waitersByKey.get(key);
            if (queue != null) {
                queue.remove(waiter);
            }
        }
    }

    private void writeToChannel(SocketChannel channel, String reply) {
        try {
            channel.write(ByteBuffer.wrap(reply.getBytes()));
        } catch (IOException e) {
        }
    }
}
