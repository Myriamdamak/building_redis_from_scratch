package command;

import java.nio.channels.SocketChannel;

/**
 * Carries per-request context a command might need beyond just its
 * arguments - right now, just the requesting client's channel, so BLPOP
 * can register that client as a waiter if it needs to block.
 */
public class CommandContext {
    public final SocketChannel channel;

    public CommandContext(SocketChannel channel) {
        this.channel = channel;
    }
}
