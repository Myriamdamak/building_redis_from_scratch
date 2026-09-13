package command;

import protocol.RespEncoder;
import store.StringStore;

import java.util.List;

public class SetCommand implements Command {

    private final StringStore store;

    public SetCommand(StringStore store) {
        this.store = store;
    }

    @Override
    public String execute(List<String> args,CommandContext context) {
        if (args.size() < 3) {
            return RespEncoder.error("ERR wrong number of arguments for 'set' command");
        }

        String key = args.get(1);
        String value = args.get(2);
        Long expiryAt = null;

        // Parse optional arguments (PX, EX, etc.) starting after key/value
        for (int i = 3; i < args.size(); i++) {
            String option = args.get(i).toUpperCase();

            if (option.equals("PX")) {
                if (i + 1 >= args.size()) {
                    return RespEncoder.error("ERR syntax error");
                }
                long millis;
                try {
                    millis = Long.parseLong(args.get(i + 1));
                } catch (NumberFormatException e) {
                    return RespEncoder.error("ERR value is not an integer or out of range");
                }
                expiryAt = System.currentTimeMillis() + millis;
                i++;
            } else if (option.equals("EX")) {
                if (i + 1 >= args.size()) {
                    return RespEncoder.error("ERR syntax error");
                }
                long seconds;
                try {
                    seconds = Long.parseLong(args.get(i + 1));
                } catch (NumberFormatException e) {
                    return RespEncoder.error("ERR value is not an integer or out of range");
                }
                expiryAt = System.currentTimeMillis() + (seconds * 1000);
                i++;
            }
            // Unknown options are silently ignored for now
        }

        store.set(key, value, expiryAt);
        return RespEncoder.simpleString("OK");
    }
}
