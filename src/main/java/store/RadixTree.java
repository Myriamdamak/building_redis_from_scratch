package store;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * A compressed radix tree (also called a PATRICIA trie, or "rax" - the
 * name real Redis uses for this exact structure to store stream entries).
 *
 * Unlike a plain trie (one tree node per character), a radix tree merges
 * chains of single-child nodes into one edge labeled with a whole
 * substring. This keeps the tree shallow and memory-efficient, which
 * matters for streams that can have millions of entries with long,
 * mostly-shared ID prefixes.

 */
public class RadixTree<V> {

    private static class Node<V> {
        // The substring of the key this edge (from parent to this node) represents.
        // Empty for the root, since the root has no incoming edge.
        String edgeLabel;

        // Sorted by first character of each child's edge label, so
        // iteration order == key order.
        TreeMap<Character, Node<V>> children = new TreeMap<>();

        boolean isTerminal; // true if a key actually ends exactly here
        V value;

        Node(String edgeLabel) {
            this.edgeLabel = edgeLabel;
        }
    }

    private final Node<V> root = new Node<>("");

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
            // No edge starts with this character yet - just attach a new
            // leaf holding the entire remaining key.
            Node<V> newNode = new Node<>(key);
            newNode.isTerminal = true;
            newNode.value = value;
            node.children.put(firstChar, newNode);
            return;
        }

        int commonLen = commonPrefixLength(key, child.edgeLabel);

        if (commonLen == child.edgeLabel.length()) {
            // The whole edge matched - recurse into the child with
            // whatever's left of the key.
            insert(child, key.substring(commonLen), value);
            return;
        }

        // Only part of the edge matched - split it.
        // Example: edge is "1526919030474-0", inserting "1526919030475-0".
        // Common prefix: "152691903047". We need a new branch point there.

        // 1. New intermediate node holding just the common prefix.
        Node<V> splitNode = new Node<>(child.edgeLabel.substring(0, commonLen));

        // 2. Shrink the existing child's edge to whatever wasn't shared,
        //    and hang it under the new split node.
        child.edgeLabel = child.edgeLabel.substring(commonLen);
        splitNode.children.put(child.edgeLabel.charAt(0), child);

        // 3. Replace the old child with the split node at this position.
        node.children.put(firstChar, splitNode);

        // 4. Attach whatever's left of the new key under the split node.
        String remainingKey = key.substring(commonLen);
        if (remainingKey.isEmpty()) {
            // The new key ends exactly at the split point.
            splitNode.isTerminal = true;
            splitNode.value = value;
        } else {
            Node<V> newLeaf = new Node<>(remainingKey);
            newLeaf.isTerminal = true;
            newLeaf.value = value;
            splitNode.children.put(remainingKey.charAt(0), newLeaf);
        }
    }

    /** Returns every value in the tree, in ascending key order. */
    public List<V> collectAllInOrder() {
        List<V> results = new ArrayList<>();
        collect(root, results);
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

    private static int commonPrefixLength(String a, String b) {
        int max = Math.min(a.length(), b.length());
        int i = 0;
        while (i < max && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }


    public V findMax() {
        return findMax(root, null);
    }

    private V findMax(Node<V> node, V currentMax) {
        if (node.isTerminal) {
            if (currentMax == null) {
                currentMax = node.value;
            } else {
                StreamEntry currentEntry = (StreamEntry) currentMax;
                StreamEntry candidateEntry = (StreamEntry) node.value;

                if (compareStreamIds(candidateEntry.id, currentEntry.id) > 0) {
                    currentMax = node.value;
                }
            }
        }

        for (Node<V> child : node.children.values()) {
            currentMax = findMax(child, currentMax);
        }

        return currentMax;
    }

    private int compareStreamIds(String id1, String id2) {
        String[] parts1 = id1.split("-");
        String[] parts2 = id2.split("-");

        long ms1 = Long.parseLong(parts1[0]);
        long seq1 = Long.parseLong(parts1[1]);

        long ms2 = Long.parseLong(parts2[0]);
        long seq2 = Long.parseLong(parts2[1]);

        if (ms1 != ms2) {
            return Long.compare(ms1, ms2);
        }

        return Long.compare(seq1, seq2);
    }


}