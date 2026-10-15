import java.io.*;
import java.util.*;

// Reads one RESP command from the client and returns it as a list of words
public class RespParser {

    public static List<String> parseCommand(BufferedReader reader) throws IOException {
        String line = reader.readLine();

        if (line == null) {
            return null; // client disconnected
        }

        if (!line.startsWith("*")) {
            return new ArrayList<>();
        }

        int numElements = Integer.parseInt(line.substring(1));
        List<String> command = new ArrayList<>();

        for (int i = 0; i < numElements; i++) {
            String lengthLine = reader.readLine();
            String value = reader.readLine();
            command.add(value);
        }

        return command;
    }
}