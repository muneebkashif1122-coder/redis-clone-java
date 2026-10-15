import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

// Talks to ONE client: read a command, run it, send the reply, repeat
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final CommandHandler commandHandler;

    public ClientHandler(Socket socket, CommandHandler commandHandler) {
        this.socket = socket;
        this.commandHandler = commandHandler;
    }

    @Override
    public void run() {
        try (Socket s = socket) { // socket is closed automatically at the end
            OutputStream output = s.getOutputStream();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));

            while (true) {
                List<String> command = RespParser.parseCommand(reader);
                if (command == null) {
                    break; // client disconnected
                }

                System.out.println("Received command: " + command);

                String response = commandHandler.execute(command);
                output.write(response.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            System.out.println("Client disconnected.");

        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
        }
    }
}