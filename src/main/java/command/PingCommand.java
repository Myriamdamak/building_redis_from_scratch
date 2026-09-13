package command;

import protocol.RespEncoder;

import java.util.List;

public class PingCommand implements Command {
    @Override
    public String execute(List<String> args,CommandContext context) {
        return RespEncoder.simpleString("PONG");
    }
}

