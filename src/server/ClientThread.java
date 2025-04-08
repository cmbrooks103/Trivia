package server;

import java.io.*;
import java.net.*;

public class ClientThread implements Runnable {
    private final Socket socket;
    private final TriviaServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String clientId;

    public ClientThread(Socket socket, TriviaServer server) {
        this.socket = socket;
        this.server = server;
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("Error creating client thread: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String message = in.readLine();
            if (message != null && message.startsWith("CLIENT_ID:")) {
                clientId = message.substring(10);
                server.addClient(this);
                System.out.println("Client " + clientId + " connected");
            }
            while ((message = in.readLine()) != null) {
                if (message.startsWith("ANSWER:")) {
                    int answer = Integer.parseInt(message.substring(7));
                    server.processAnswer(clientId, answer);
                }
            }
        } catch (IOException e) {
            System.err.println("Client " + clientId + " disconnected");
        } finally {
            server.removeClient(this);
            try { socket.close(); } catch (IOException e) {}
        }
    }

    public void sendMessage(String message) throws IOException {
        out.println(message);
    }

    public String getClientId() {
        return clientId;
    }

    // Forcefully close the client's connection.
    public void kill() {
        try {
            socket.close();
        } catch(IOException e) {
            // Silent catch.
        }
    }
}