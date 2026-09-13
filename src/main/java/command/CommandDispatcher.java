package command;
import protocol.RespEncoder;
import store.ListStore;
import store.StreamStore;
import store.StringStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds a name -> Command lookup table and routes each parsed command to
 * the right implementation. Add a new command by writing a new Command
 * class and registering it in the constructor below.
 */
public class CommandDispatcher {

    private final Map<String, Command> commands = new HashMap<>();

    public CommandDispatcher(StringStore stringStore, ListStore listStore, StreamStore streamStore) {
        commands.put("PING", new PingCommand());
        commands.put("ECHO", new EchoCommand());
        commands.put("SET", new SetCommand(stringStore));
        commands.put("GET", new GetCommand(stringStore));
        commands.put("RPUSH", new RpushCommand(listStore));
        commands.put("LPUSH", new LpushCommand(listStore));
        commands.put("LLEN", new LlenCommand(listStore));
        commands.put("LPOP", new LpopCommand(listStore));
        commands.put("LRANGE", new LrangeCommand(listStore));
        commands.put("BLPOP", new BlpopCommand(listStore));
        commands.put("Type",new TypeCommand(stringStore,streamStore));
        commands.put("XADD",new XaddCommand(streamStore));

    }

    public String dispatch(List<String> args, CommandContext context) {
        if (args.isEmpty()) {
            return null;
        }

        String commandName = args.get(0).toUpperCase();
        Command command = commands.get(commandName);

        if (command == null) {
            return RespEncoder.error("ERR unknown command '" + commandName + "'");
        }

        return command.execute(args, context);
    }
}

