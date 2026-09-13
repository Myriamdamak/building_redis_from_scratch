package command;

import protocol.RespEncoder;

import java.util.List;

public class EchoCommand implements Command {
    @Override
    public String execute(List<String> args,CommandContext context) {
        if (args.size() < 2) {
            return RespEncoder.error("ERR wrong number of arguments for 'echo' command");
        }
        return RespEncoder.bulkString(args.get(1));
    }
}
