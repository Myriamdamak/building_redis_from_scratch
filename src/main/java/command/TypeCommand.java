package command;

import java.util.List;
import protocol.RespEncoder;
import store.StringStore;


public class TypeCommand implements Command {
    private final StringStore store;

    public TypeCommand(StringStore store) {
        this.store = store;
    }
    @Override
    public String execute(List<String> args, CommandContext context) {
        String type="none" ;
        if (args.size() < 2) {
            return RespEncoder.error("ERR wrong number of arguments for 'type' command");
        }

        String key = args.get(1);
        String value = store.get(key);

        if (value != null) {
            type = "string";
        }


        return RespEncoder.simpleString(type);
    }

}
