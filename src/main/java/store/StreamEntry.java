package store;

import java.util.List;


public class StreamEntry {

    public final String id;
    public final List<String> fields;

    public StreamEntry(String id, List<String> fields) {
        this.id = id;
        this.fields = fields;
    }
}