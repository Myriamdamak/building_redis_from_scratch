package command;

import java.util.List;


public interface Command {
        String execute(List<String> args, CommandContext context);
}
