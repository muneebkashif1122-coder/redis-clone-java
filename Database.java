import java.util.*;
import java.util.concurrent.*;

public class Database {

    public final ConcurrentHashMap<String, String> strings = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, LinkedList<String>> lists = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, LinkedHashMap<String, String>> hashes = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, Long> expiry = new ConcurrentHashMap<>();

    // If the key has expired, remove it from every store. Returns true if it was expired.
    public boolean isExpired(String key) {
        Long expiryTime = expiry.get(key);
        if (expiryTime == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiryTime) {
            strings.remove(key);
            lists.remove(key);
            hashes.remove(key);
            expiry.remove(key);
            return true;
        }
        return false;
    }

    // True if the key exists (as any type) and has not expired
    public boolean keyExists(String key) {
        if (isExpired(key)) {
            return false;
        }
        return strings.containsKey(key)
            || lists.containsKey(key)
            || hashes.containsKey(key);
    }
}