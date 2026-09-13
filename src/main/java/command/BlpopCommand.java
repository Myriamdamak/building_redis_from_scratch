package command;

import protocol.RespEncoder;
import store.ListStore;
import store.Waiter;

import java.util.ArrayList;
import java.util.List;

/**
 * BLPOP key [key ...] timeout
 *
 * Pops the first available element from the first non-empty list among
 * the given keys. If every given list is currently empty, the client
 * blocks until an element becomes available (via RPUSH/LPUSH on one of
 * the watched keys) or the timeout elapses.
 *
 * timeout is in seconds; 0 means "block forever" (real Redis behavior).
 *
 * Returning null from execute() tells the dispatcher/event loop "don't
 * write a reply now" - either the reply was already written directly to
 * context.channel (not through the normal return path), or the client
 * has been registered as a waiter and will be replied to later by
 * ListStore, once data arrives or the wait times out.
 */
public class BlpopCommand implements Command {

    private final ListStore store;

    public BlpopCommand(ListStore store) {
        this.store = store;
    }

    @Override
    public String execute(List<String> args, CommandContext context) {
        // BLPOP key1 key2 ... keyN timeout -> at least 3 tokens total
        if (args.size() < 3) {
            return RespEncoder.error("ERR wrong number of arguments for 'blpop' command");
        }

        List<String> keys = args.subList(1, args.size() - 1);
        String timeoutArg = args.get(args.size() - 1);

        double timeoutSeconds;
        try {
            timeoutSeconds = Double.parseDouble(timeoutArg);
        } catch (NumberFormatException e) {
            return RespEncoder.error("ERR timeout is not a float or out of range");
        }

        if (timeoutSeconds < 0) {
            return RespEncoder.error("ERR timeout is negative");
        }

        // Check every requested key right away - if any already has data,
        // pop from the first one that does and reply immediately, no blocking needed.
        for (String key : keys) {
            List<String> list = store.get(key);
            if (list != null && !list.isEmpty()) {
                String value = list.remove(0);

                List<String> encoded = new ArrayList<>();
                encoded.add(RespEncoder.bulkString(key));
                encoded.add(RespEncoder.bulkString(value));
                return RespEncoder.array(encoded);
            }
        }

        // Nothing available right now - register this client as a waiter and block.
        Long deadlineMillis = (timeoutSeconds == 0)
                ? null
                : System.currentTimeMillis() + (long) (timeoutSeconds * 1000);

        Waiter waiter = new Waiter(new ArrayList<>(keys), context.channel, deadlineMillis);
        store.addWaiter(waiter);

        // No immediate reply - ListStore delivers one later, either when a
        // push arrives on a watched key, or when checkExpiredWaiters() times it out.
        return null;
    }
}
