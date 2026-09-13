package command;

import protocol.RespEncoder;
import store.ListStore;

import java.util.ArrayList;
import java.util.List;

public class LpopCommand implements Command {

    private final ListStore store;

    public LpopCommand(ListStore store) {
        this.store = store;
    }

    @Override
    public String execute(List<String> args,CommandContext context) {
        if (args.size() < 2) {
            return RespEncoder.error("ERR wrong number of arguments for 'lpop' command");
        }

        String key = args.get(1);
        List<String> list = store.get(key);
        boolean countGiven = args.size() >= 3;

        if (list == null || list.isEmpty()) {
            // No count -> nil bulk string. Count given -> empty array.
            return countGiven ? RespEncoder.emptyArray() : RespEncoder.bulkString(null);
        }

        if (!countGiven) {
            // Plain LPOP key -> pop exactly one, bulk string reply
            String popped = list.remove(0);
            return RespEncoder.bulkString(popped);
        }

        // LPOP key count -> pop up to 'count' elements, array reply
        int numToPop;
        try {
            numToPop = Integer.parseInt(args.get(2));
        } catch (NumberFormatException e) {
            return RespEncoder.error("ERR value is not an integer or out of range");
        }

        if (numToPop < 0) {
            return RespEncoder.error("ERR value is out of range, must be positive");
        }

        List<String> encodedItems = new ArrayList<>();
        for (int i = 0; i < numToPop && !list.isEmpty(); i++) {
            String popped = list.remove(0);
            encodedItems.add(RespEncoder.bulkString(popped));
        }

        return RespEncoder.array(encodedItems);
    }
}
