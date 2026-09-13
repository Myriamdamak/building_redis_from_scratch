package protocol;

import java.util.List;


public class RespEncoder {
    public static String simpleString(String s) { return "+" + s + "\r\n"; }
    public static String error(String msg) { return "-" + msg + "\r\n"; }
    public static String integer(long n) { return ":" + n + "\r\n"; }
    public static String bulkString(String s) {
        if (s == null) return "$-1\r\n";
        return "$" + s.length() + "\r\n" + s + "\r\n";
    }
    public static String array(List<String> encodedItems) {
        StringBuilder sb = new StringBuilder("*").append(encodedItems.size()).append("\r\n");
        for (String item : encodedItems) sb.append(item);
        return sb.toString();
    }
    public static String nullArray() { return "*-1\r\n"; }
    public static String emptyArray() { return "*0\r\n"; }
}