package command;

import protocol.RespEncoder;
import store.ListStore;

import java.util.List;

public class LlenCommand implements Command {

    private final ListStore store;

    public LlenCommand(ListStore store) {
        this.store = store;
    }

    @Override
    public String execute(List<String> args,CommandContext context) {
        if (args.size() < 2) {
            return RespEncoder.error("ERR wrong number of arguments for 'llen' command");
        }

        String key = args.get(1);
        List<String> list = store.get(key);

        if (list == null) {
            return RespEncoder.integer(0);
        }
        return RespEncoder.integer(list.size());
    }
}

