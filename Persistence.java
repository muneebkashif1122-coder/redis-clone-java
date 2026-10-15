import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Persistence {

    private static final String DB_FILE = "dump.dat";
    private static final byte TYPE_END = 0;
    private static final byte TYPE_STRING = 1;
    private static final byte TYPE_LIST = 2;
    private static final byte TYPE_HASH = 3;

    private final Database db;
    private final Object saveLock = new Object();

    public Persistence(Database db) {
        this.db = db;
    }

    public boolean save() {
        synchronized (saveLock) {
            Path tmpPath = Paths.get(DB_FILE + ".tmp");
            Path finalPath = Paths.get(DB_FILE);
            long now = System.currentTimeMillis();

            try {
                try (DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(tmpPath)))) {

                    for (Map.Entry<String, String> entry : db.strings.entrySet()) {
                        String key = entry.getKey();
                        Long exp = db.expiry.get(key);
                        if (exp != null && now >= exp) continue;

                        out.writeByte(TYPE_STRING);
                        out.writeUTF(key);
                        out.writeLong(exp == null ? -1 : exp);
                        out.writeUTF(entry.getValue());
                    }

                    for (Map.Entry<String, LinkedList<String>> entry : db.lists.entrySet()) {
                        String key = entry.getKey();
                        Long exp = db.expiry.get(key);
                        if (exp != null && now >= exp) continue;

                        List<String> copy;
                        synchronized (entry.getValue()) {
                            copy = new ArrayList<>(entry.getValue());
                        }

                        out.writeByte(TYPE_LIST);
                        out.writeUTF(key);
                        out.writeLong(exp == null ? -1 : exp);
                        out.writeInt(copy.size());
                        for (String item : copy) {
                            out.writeUTF(item);
                        }
                    }

                    for (Map.Entry<String, LinkedHashMap<String, String>> entry : db.hashes.entrySet()) {
                        String key = entry.getKey();
                        Long exp = db.expiry.get(key);
                        if (exp != null && now >= exp) continue;

                        Map<String, String> copy;
                        synchronized (entry.getValue()) {
                            copy = new LinkedHashMap<>(entry.getValue());
                        }

                        out.writeByte(TYPE_HASH);
                        out.writeUTF(key);
                        out.writeLong(exp == null ? -1 : exp);
                        out.writeInt(copy.size());
                        for (Map.Entry<String, String> field : copy.entrySet()) {
                            out.writeUTF(field.getKey());
                            out.writeUTF(field.getValue());
                        }
                    }

                    out.writeByte(TYPE_END);
                }

                Files.move(tmpPath, finalPath,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                System.out.println("Data saved to " + DB_FILE);
                return true;

            } catch (IOException e) {
                System.out.println("Error saving data: " + e.getMessage());
                return false;
            }
        }
    }

    public void load() {
        Path path = Paths.get(DB_FILE);
        if (!Files.exists(path)) {
            System.out.println("No saved data found, starting empty.");
            return;
        }

        long now = System.currentTimeMillis();
        int loaded = 0;

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {

            while (true) {
                byte type = in.readByte();
                if (type == TYPE_END) break;

                String key = in.readUTF();
                long exp = in.readLong();
                boolean expired = (exp != -1 && now >= exp);

                if (type == TYPE_STRING) {
                    String value = in.readUTF();
                    if (!expired) {
                        db.strings.put(key, value);
                        if (exp != -1) db.expiry.put(key, exp);
                        loaded++;
                    }
                } else if (type == TYPE_LIST) {
                    int size = in.readInt();
                    LinkedList<String> list = new LinkedList<>();
                    for (int i = 0; i < size; i++) {
                        list.add(in.readUTF());
                    }
                    if (!expired) {
                        db.lists.put(key, list);
                        if (exp != -1) db.expiry.put(key, exp);
                        loaded++;
                    }
                } else if (type == TYPE_HASH) {
                    int size = in.readInt();
                    LinkedHashMap<String, String> hash = new LinkedHashMap<>();
                    for (int i = 0; i < size; i++) {
                        String field = in.readUTF();
                        String value = in.readUTF();
                        hash.put(field, value);
                    }
                    if (!expired) {
                        db.hashes.put(key, hash);
                        if (exp != -1) db.expiry.put(key, exp);
                        loaded++;
                    }
                } else {
                    System.out.println("Unknown entry type in " + DB_FILE + ", stopping load.");
                    break;
                }
            }

            System.out.println("Loaded " + loaded + " keys from " + DB_FILE);

        } catch (IOException e) {
            System.out.println("Error loading data: " + e.getMessage());
        }
    }
}