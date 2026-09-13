package command;

import protocol.RespEncoder;
import store.ListStore;

import java.util.List;

public class LpushCommand implements Command {

    private final ListStore store;

    public LpushCommand(ListStore store) {
        this.store = store;
    }

    @Override
    public String execute(List<String> args,CommandContext context) {
        if (args.size() < 3) {
            return RespEncoder.error("ERR wrong number of arguments for 'lpush' command");
        }

        String key = args.get(1);
        List<String> list = store.getOrCreate(key);

        for (int i = 2; i < args.size(); i++) {
            list.add(0, args.get(i));
        }

        return RespEncoder.integer(list.size());
    }
}
