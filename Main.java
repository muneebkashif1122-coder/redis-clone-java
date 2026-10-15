import java.io.*;
import java.net.*;

public class Main {

    public static void main(String[] args) {
        System.out.println("Starting Redis clone server...");

        Database db = new Database();
        Persistence persistence = new Persistence(db);
        persistence.load();

        // Runs automatically when the server is stopped with Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down, saving data...");
            persistence.save();
        }));

        CommandHandler commandHandler = new CommandHandler(db, persistence);

        try {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true); // must be set BEFORE bind()
            serverSocket.bind(new InetSocketAddress(6379));
            System.out.println("Server is listening on port 6379");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("A client connected!");

                new Thread(new ClientHandler(clientSocket, commandHandler)).start();
            }

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}