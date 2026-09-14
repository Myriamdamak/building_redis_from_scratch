package command;

import protocol.RespEncoder;
import store.RadixTree;
import store.StreamEntry;
import store.StreamStore;

import java.util.ArrayList;
import java.util.List;

/**
 * XRANGE key start end
 *
 * Returns all entries with IDs between start and end (inclusive).
 *
 * start/end each independently support three forms:
 *   "-"          -> the lowest possible ID in the stream (0-1)
 *   "+"          -> the highest possible ID in the stream (unbounded)
 *   "<ms>"       -> sequence number omitted; defaults to 0 for start,
 *                   or the maximum possible sequence for end
 *   "<ms>-<seq>" -> fully specified, used as-is
 */
public class XrangeCommand implements Command {

    private final StreamStore store;

    public XrangeCommand(StreamStore store) {
        this.store = store;
    }

    @Override
    public String execute(List<String> args, CommandContext context) {
        if (args.size() != 4) {
            return RespEncoder.error("ERR wrong number of arguments for 'xrange' command");
        }

        String key = args.get(1);
        String rawStart = args.get(2);
        String rawEnd = args.get(3);

        RadixTree<StreamEntry> tree = store.get(key);
        if (tree == null) {
            return RespEncoder.emptyArray();
        }

        String start = normalizeId(rawStart, true);
        String end = normalizeId(rawEnd, false);

        List<StreamEntry> entries = tree.collectRange(start, end);

        List<String> encodedEntries = new ArrayList<>();
        for (StreamEntry entry : entries) {
            List<String> fieldArray = new ArrayList<>();
            for (String field : entry.fields) {
                fieldArray.add(RespEncoder.bulkString(field));
            }

            // Each entry encodes as [id, [field, value, field, value, ...]]
            List<String> encodedEntry = new ArrayList<>();
            encodedEntry.add(RespEncoder.bulkString(entry.id));
            encodedEntry.add(RespEncoder.array(fieldArray));

            encodedEntries.add(RespEncoder.array(encodedEntry));
        }

        return RespEncoder.array(encodedEntries);
    }


    private String normalizeId(String rawId, boolean isStart) {
        if (rawId.equals("-")) {
            return "0-1";
        }

        if (rawId.equals("+")) {
            return Long.MAX_VALUE + "-" + Long.MAX_VALUE;
        }

        if (rawId.contains("-")) {
            return rawId;
        }


        String defaultSeq = isStart ? "0" : String.valueOf(Long.MAX_VALUE);
        return rawId + "-" + defaultSeq;
    }
}