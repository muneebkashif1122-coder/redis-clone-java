import java.nio.charset.StandardCharsets;

// Helpers that build replies in RESP format
public class RespWriter {

    public static String simple(String s) {
        return "+" + s + "\r\n";
    }

    public static String error(String message) {
        return "-" + message + "\r\n";
    }

    public static String integer(long n) {
        return ":" + n + "\r\n";
    }

    // Length is counted in BYTES (matters for non-English text)
    public static String bulk(String s) {
        int byteLength = s.getBytes(StandardCharsets.UTF_8).length;
        return "$" + byteLength + "\r\n" + s + "\r\n";
    }

    public static String nil() {
        return "$-1\r\n";
    }

    public static String arrayHeader(int count) {
        return "*" + count + "\r\n";
    }

    public static String emptyArray() {
        return "*0\r\n";
    }
}