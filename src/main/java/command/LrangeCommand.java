package command;

import protocol.RespEncoder;
import store.ListStore;

import java.util.ArrayList;
import java.util.List;

public class LrangeCommand implements Command {

    private final ListStore store;

    public LrangeCommand(ListStore store) {
        this.store = store;
    }

    @Override
    public String execute(List<String> args,CommandContext context) {
        if (args.size() < 4) {
            return RespEncoder.error("ERR wrong number of arguments for 'lrange' command");
        }

        String key = args.get(1);
        List<String> list = store.get(key);

        if (list == null) {
            return RespEncoder.emptyArray();
        }

        int start;
        int end;
        try {
            start = Integer.parseInt(args.get(2));
            end = Integer.parseInt(args.get(3));
        } catch (NumberFormatException e) {
            return RespEncoder.error("ERR value is not an integer or out of range");
        }

        int size = list.size();

        // Clamp negative indices and out-of-range bounds, like real Redis
        if (start < 0) start = Math.max(size + start, 0);
        if (end < 0) end = size + end;
        if (end >= size) end = size - 1;

        if (start > end || start >= size || size == 0) {
            return RespEncoder.emptyArray();
        }

        List<String> encodedItems = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            encodedItems.add(RespEncoder.bulkString(list.get(i)));
        }

        return RespEncoder.array(encodedItems);
    }
}
