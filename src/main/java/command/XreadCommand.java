package command;

import protocol.RespEncoder;
import store.RadixTree;
import store.StreamEntry;
import store.StreamStore;

import java.util.ArrayList;
import java.util.List;

public class XreadCommand implements Command{
     StreamStore store=new StreamStore();
    public XreadCommand (StreamStore store){
        this.store=store;
    }

    @Override
    public String execute(List<String> args, CommandContext context) {
        if(args.size()<4|| (args.size() - 2) % 2 != 0){
            return RespEncoder.error("ERR wrong number of arguments for 'xread streams' command");
        }
        String key = args.get(2);
        String rawStart = args.get(3);


        RadixTree<StreamEntry> tree = store.get(key);
        if (tree == null) {
            return RespEncoder.emptyArray();
        }

        String start = normalizeId(rawStart, true);

        List<StreamEntry> entries = tree.collectRange(start,Long.MAX_VALUE + "-" + Long.MAX_VALUE,true);

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

        if (rawId.contains("-")) {
            return rawId;
        }


        String defaultSeq = isStart ? "0" : String.valueOf(Long.MAX_VALUE);
        return rawId + "-" + defaultSeq;
    }



    }


