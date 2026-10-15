import java.util.*;

public class CommandHandler {

    private static final String WRONGTYPE =
        RespWriter.error("WRONGTYPE Operation against a key holding the wrong kind of value");

    private final Database db;
    private final Persistence persistence;

    public CommandHandler(Database db, Persistence persistence) {
        this.db = db;
        this.persistence = persistence;
    }

    // Takes a parsed command like [SET, name, John] and returns the RESP reply
    public String execute(List<String> command) {
        if (command.isEmpty()) {
            return RespWriter.error("ERR unknown command");
        }

        String name = command.get(0).toUpperCase();

        try {
            switch (name) {
                case "PING":    return RespWriter.simple("PONG");
                case "SAVE":    return save();
                case "SET":     return set(command);
                case "GET":     return get(command);
                case "DEL":     return del(command);
                case "EXISTS":  return exists(command);
                case "EXPIRE":  return expire(command);
                case "TTL":     return ttl(command);
                case "LPUSH":   return push(command, true);
                case "RPUSH":   return push(command, false);
                case "LRANGE":  return lrange(command);
                case "HSET":    return hset(command);
                case "HGET":    return hget(command);
                case "HGETALL": return hgetall(command);
                case "HDEL":    return hdel(command);
                // Unknown commands (like redis-cli's COMMAND DOCS) get an empty array
                default:        return RespWriter.emptyArray();
            }
        } catch (NumberFormatException e) {
            return RespWriter.error("ERR value is not an integer or out of range");
        }
    }

    private String wrongArgs(String cmd) {
        return RespWriter.error("ERR wrong number of arguments for '" + cmd + "'");
    }

    // ---------- General ----------

    private String save() {
        return persistence.save()
            ? RespWriter.simple("OK")
            : RespWriter.error("ERR could not save data");
    }

    private String del(List<String> c) {
        if (c.size() < 2) return wrongArgs("DEL");
        String key = c.get(1);
        if (db.isExpired(key)) return RespWriter.integer(0);

        boolean removed = false;
        if (db.strings.remove(key) != null) removed = true;
        if (db.lists.remove(key) != null) removed = true;
        if (db.hashes.remove(key) != null) removed = true;
        db.expiry.remove(key);
        return RespWriter.integer(removed ? 1 : 0);
    }

    private String exists(List<String> c) {
        if (c.size() < 2) return wrongArgs("EXISTS");
        return RespWriter.integer(db.keyExists(c.get(1)) ? 1 : 0);
    }

    private String expire(List<String> c) {
        if (c.size() < 3) return wrongArgs("EXPIRE");
        String key = c.get(1);
        if (!db.keyExists(key)) return RespWriter.integer(0);

        long seconds = Long.parseLong(c.get(2));
        db.expiry.put(key, System.currentTimeMillis() + (seconds * 1000));
        return RespWriter.integer(1);
    }

    private String ttl(List<String> c) {
        if (c.size() < 2) return wrongArgs("TTL");
        String key = c.get(1);
        if (!db.keyExists(key)) return RespWriter.integer(-2);

        Long expiryTime = db.expiry.get(key);
        if (expiryTime == null) return RespWriter.integer(-1);

        long remainingSeconds = (expiryTime - System.currentTimeMillis()) / 1000;
        return RespWriter.integer(remainingSeconds);
    }

    // ---------- Strings ----------

    private String set(List<String> c) {
        if (c.size() < 3) return wrongArgs("SET");
        String key = c.get(1);
        db.lists.remove(key);   // SET overwrites whatever type was there
        db.hashes.remove(key);
        db.strings.put(key, c.get(2));
        db.expiry.remove(key);
        return RespWriter.simple("OK");
    }

    private String get(List<String> c) {
        if (c.size() < 2) return wrongArgs("GET");
        String key = c.get(1);
        if (db.isExpired(key)) return RespWriter.nil();
        if (db.lists.containsKey(key) || db.hashes.containsKey(key)) return WRONGTYPE;

        String value = db.strings.get(key);
        return value == null ? RespWriter.nil() : RespWriter.bulk(value);
    }

    // ---------- Lists ----------

    private String push(List<String> c, boolean left) {
        String cmd = left ? "LPUSH" : "RPUSH";
        if (c.size() < 3) return wrongArgs(cmd);
        String key = c.get(1);
        db.isExpired(key);
        if (db.strings.containsKey(key) || db.hashes.containsKey(key)) return WRONGTYPE;

        LinkedList<String> list = db.lists.computeIfAbsent(key, k -> new LinkedList<>());
        int newSize;
        synchronized (list) {
            for (int i = 2; i < c.size(); i++) {
                if (left) {
                    list.addFirst(c.get(i));
                } else {
                    list.addLast(c.get(i));
                }
            }
            newSize = list.size();
        }
        return RespWriter.integer(newSize);
    }

    private String lrange(List<String> c) {
        if (c.size() < 4) return wrongArgs("LRANGE");
        String key = c.get(1);
        if (db.isExpired(key)) return RespWriter.emptyArray();
        if (db.strings.containsKey(key) || db.hashes.containsKey(key)) return WRONGTYPE;

        LinkedList<String> list = db.lists.get(key);
        if (list == null) return RespWriter.emptyArray();

        int start = Integer.parseInt(c.get(2));
        int stop = Integer.parseInt(c.get(3));

        StringBuilder sb = new StringBuilder();
        synchronized (list) {
            int size = list.size();
            if (start < 0) start += size;   // -1 means the last element
            if (stop < 0) stop += size;
            if (start < 0) start = 0;
            if (stop >= size) stop = size - 1;
            if (start > stop || start >= size) {
                return RespWriter.emptyArray();
            }
            sb.append(RespWriter.arrayHeader(stop - start + 1));
            for (int i = start; i <= stop; i++) {
                sb.append(RespWriter.bulk(list.get(i)));
            }
        }
        return sb.toString();
    }

    // ---------- Hashes ----------

    private String hset(List<String> c) {
        // HSET key field value [field value ...]
        if (c.size() < 4 || c.size() % 2 != 0) return wrongArgs("HSET");
        String key = c.get(1);
        db.isExpired(key);
        if (db.strings.containsKey(key) || db.lists.containsKey(key)) return WRONGTYPE;

        LinkedHashMap<String, String> hash =
            db.hashes.computeIfAbsent(key, k -> new LinkedHashMap<>());
        int added = 0;
        synchronized (hash) {
            for (int i = 2; i + 1 < c.size(); i += 2) {
                // put() returns null if the field is brand new
                if (hash.put(c.get(i), c.get(i + 1)) == null) {
                    added++;
                }
            }
        }
        return RespWriter.integer(added);
    }

    private String hget(List<String> c) {
        if (c.size() < 3) return wrongArgs("HGET");
        String key = c.get(1);
        if (db.isExpired(key)) return RespWriter.nil();
        if (db.strings.containsKey(key) || db.lists.containsKey(key)) return WRONGTYPE;

        LinkedHashMap<String, String> hash = db.hashes.get(key);
        if (hash == null) return RespWriter.nil();

        String value;
        synchronized (hash) {
            value = hash.get(c.get(2));
        }
        return value == null ? RespWriter.nil() : RespWriter.bulk(value);
    }

    private String hgetall(List<String> c) {
        if (c.size() < 2) return wrongArgs("HGETALL");
        String key = c.get(1);
        if (db.isExpired(key)) return RespWriter.emptyArray();
        if (db.strings.containsKey(key) || db.lists.containsKey(key)) return WRONGTYPE;

        LinkedHashMap<String, String> hash = db.hashes.get(key);
        if (hash == null) return RespWriter.emptyArray();

        StringBuilder sb = new StringBuilder();
        synchronized (hash) {
            // Every field and every value is its own element, so count = size * 2
            sb.append(RespWriter.arrayHeader(hash.size() * 2));
            for (Map.Entry<String, String> entry : hash.entrySet()) {
                sb.append(RespWriter.bulk(entry.getKey()));
                sb.append(RespWriter.bulk(entry.getValue()));
            }
        }
        return sb.toString();
    }

    private String hdel(List<String> c) {
        if (c.size() < 3) return wrongArgs("HDEL");
        String key = c.get(1);
        if (db.isExpired(key)) return RespWriter.integer(0);
        if (db.strings.containsKey(key) || db.lists.containsKey(key)) return WRONGTYPE;

        LinkedHashMap<String, String> hash = db.hashes.get(key);
        if (hash == null) return RespWriter.integer(0);

        int deleted = 0;
        synchronized (hash) {
            for (int i = 2; i < c.size(); i++) {
                if (hash.remove(c.get(i)) != null) {
                    deleted++;
                }
            }
            if (hash.isEmpty()) {
                // A hash with no fields doesn't exist in Redis
                db.hashes.remove(key);
                db.expiry.remove(key);
            }
        }
        return RespWriter.integer(deleted);
    }
}