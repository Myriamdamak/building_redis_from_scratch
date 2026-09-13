package command;

import protocol.RespEncoder;
import store.StreamStore;
import store.StringStore;

import java.util.List;

public class TypeCommand implements Command {

    private final StringStore stringStore;
    private final StreamStore streamStore;

    public TypeCommand(StringStore stringStore, StreamStore streamStore) {
        this.stringStore = stringStore;
        this.streamStore = streamStore;
    }

    @Override
    public String execute(List<String> args, CommandContext context) {
        if (args.size() < 2) {
            return RespEncoder.error("ERR wrong number of arguments for 'type' command");
        }

        String key = args.get(1);
        String type = "none";

        if (stringStore.get(key) != null) {
            type = "string";
        } else if (streamStore.get(key) != null) {
            type = "stream";
        }

        return RespEncoder.simpleString(type);
    }



    }



