package store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * A compressed radix tree (PATRICIA trie) used to store stream entries.
 */
public class RadixTree<V> {

    private static class Node<V> {

        String edgeLabel;

        TreeMap<Character, Node<V>> children = new TreeMap<>();

        boolean isTerminal;
        V value;

        Node(String edgeLabel) {
            this.edgeLabel = edgeLabel;
        }
    }

    private final Node<V> root = new Node<>("");

    /**
     * Inserts a key-value pair into the radix tree.
     */
    public void insert(String key, V value) {
        insert(root, key, value);
    }

    private void insert(Node<V> node, String key, V value) {

        if (key.isEmpty()) {
            node.isTerminal = true;
            node.value = value;
            return;
        }

        char firstChar = key.charAt(0);
        Node<V> child = node.children.get(firstChar);

        if (child == null) {
            Node<V> newNode = new Node<>(key);
            newNode.isTerminal = true;
            newNode.value = value;

            node.children.put(firstChar, newNode);
            return;
        }

        int commonLen = commonPrefixLength(key, child.edgeLabel);

        if (commonLen == child.edgeLabel.length()) {
            insert(child, key.substring(commonLen), value);
            return;
        }

        // Split the existing edge.

        Node<V> splitNode =
                new Node<>(child.edgeLabel.substring(0, commonLen));

        child.edgeLabel =
                child.edgeLabel.substring(commonLen);

        splitNode.children.put(
                child.edgeLabel.charAt(0),
                child
        );

        node.children.put(firstChar, splitNode);

        String remainingKey = key.substring(commonLen);

        if (remainingKey.isEmpty()) {

            splitNode.isTerminal = true;
            splitNode.value = value;

        } else {

            Node<V> newLeaf =
                    new Node<>(remainingKey);

            newLeaf.isTerminal = true;
            newLeaf.value = value;

            splitNode.children.put(
                    remainingKey.charAt(0),
                    newLeaf
            );
        }
    }

    /**
     * Returns every value in numeric stream-ID order.
     */
    public List<V> collectAllInOrder() {

        List<V> results = new ArrayList<>();

        collect(root, results);

        sortByStreamId(results);

        return results;
    }

    private void collect(Node<V> node, List<V> results) {

        if (node.isTerminal) {
            results.add(node.value);
        }

        for (Node<V> child : node.children.values()) {
            collect(child, results);
        }
    }

    /**
     * Returns entries between start and end.
     *
     * @param start Start stream ID
     * @param end End stream ID
     * @param exclusiveStart true means entry.id must be strictly greater than start
     */
    public List<V> collectRange(
            String start,
            String end,
            boolean exclusiveStart
    ) {

        List<V> results = new ArrayList<>();

        collectRange(
                root,
                start,
                end,
                exclusiveStart,
                results
        );

        sortByStreamId(results);

        return results;
    }

    private void collectRange(
            Node<V> node,
            String start,
            String end,
            boolean exclusiveStart,
            List<V> results
    ) {

        if (node.isTerminal) {

            StreamEntry entry = (StreamEntry) node.value;

            int startComparison =
                    compareStreamIds(entry.id, start);

            int endComparison =
                    compareStreamIds(entry.id, end);

            boolean afterStart;

            if (exclusiveStart) {
                afterStart = startComparison > 0;
            } else {
                afterStart = startComparison >= 0;
            }

            if (afterStart && endComparison <= 0) {
                results.add(node.value);
            }
        }

        for (Node<V> child : node.children.values()) {
            collectRange(
                    child,
                    start,
                    end,
                    exclusiveStart,
                    results
            );
        }
    }

    /**
     * Finds the entry with the maximum stream ID.
     */
    public V findMax() {
        return findMax(root, null);
    }

    private V findMax(Node<V> node, V currentMax) {

        if (node.isTerminal) {

            if (currentMax == null) {

                currentMax = node.value;

            } else {

                StreamEntry currentEntry =
                        (StreamEntry) currentMax;

                StreamEntry candidateEntry =
                        (StreamEntry) node.value;

                if (compareStreamIds(
                        candidateEntry.id,
                        currentEntry.id
                ) > 0) {

                    currentMax = node.value;
                }
            }
        }

        for (Node<V> child : node.children.values()) {
            currentMax = findMax(child, currentMax);
        }

        return currentMax;
    }

    /**
     * Sorts stream entries using numeric stream-ID ordering.
     */
    private void sortByStreamId(List<V> results) {

        results.sort(new Comparator<V>() {

            @Override
            public int compare(V a, V b) {

                StreamEntry entryA =
                        (StreamEntry) a;

                StreamEntry entryB =
                        (StreamEntry) b;

                return compareStreamIds(
                        entryA.id,
                        entryB.id
                );
            }
        });
    }

    private static int commonPrefixLength(
            String a,
            String b
    ) {

        int max = Math.min(a.length(), b.length());

        int i = 0;

        while (
                i < max &&
                        a.charAt(i) == b.charAt(i)
        ) {
            i++;
        }

        return i;
    }

    /**
     * Compares Redis Stream IDs numerically.
     *
     * Example:
     * 2-0 < 10-0
     */
    private static int compareStreamIds(
            String id1,
            String id2
    ) {

        String[] parts1 = id1.split("-", 2);
        String[] parts2 = id2.split("-", 2);

        long ms1 = Long.parseLong(parts1[0]);
        long seq1 = Long.parseLong(parts1[1]);

        long ms2 = Long.parseLong(parts2[0]);
        long seq2 = Long.parseLong(parts2[1]);

        int msComparison = Long.compare(ms1, ms2);

        if (msComparison != 0) {
            return msComparison;
        }

        return Long.compare(seq1, seq2);
    }
}