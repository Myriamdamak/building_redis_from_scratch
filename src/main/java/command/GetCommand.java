package command;


import protocol.RespEncoder;
import store.StringStore;

import java.util.List;

public class GetCommand implements Command {

    private final StringStore store;

    public GetCommand(StringStore store) {
        this.store = store;
    }

    @Override
    public String execute(List<String> args,CommandContext context) {
        if (args.size() < 2) {
            return RespEncoder.error("ERR wrong number of arguments for 'get' command");
        }

        String key = args.get(1);
        String value = store.get(key);

        // bulkString(null) automatically becomes the RESP nil reply ($-1\r\n)
        return RespEncoder.bulkString(value);
    }
}
