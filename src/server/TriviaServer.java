package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TriviaServer {
    private ServerSocket tcpSocket;
    private DatagramSocket udpSocket;
    private ExecutorService threadPool;
    private QuestionPool questionPool;
    private ConcurrentLinkedQueue<BuzzMessage> buzzQueue;
    private Map<String, Integer> scores;
    private List<ClientThread> clients;
    private int currentQuestion = 1;
    private boolean questionActive = false;
    private Timer questionTimer;

    public static void main(String[] args) {
        new TriviaServer().start();
    }

    public void start() {
        try {
            tcpSocket = new ServerSocket(6000);
            udpSocket = new DatagramSocket(5000);
            threadPool = Executors.newCachedThreadPool();
            questionPool = new QuestionPool();
            buzzQueue = new ConcurrentLinkedQueue<>();
            scores = new ConcurrentHashMap<>();
            clients = new CopyOnWriteArrayList<>();

            System.out.println("Server started on TCP:6000 UDP:5000");
            System.out.println("Loaded " + questionPool.getQuestionCount() + " questions");

            new Thread(new UDPListener()).start();
            startQuestionTimer();

            while (true) {
                Socket clientSocket = tcpSocket.accept();
                ClientThread client = new ClientThread(clientSocket, this);
                threadPool.execute(client);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private void startQuestionTimer() {
        questionTimer = new Timer();
        questionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (currentQuestion <= questionPool.getQuestionCount()) {
                    sendQuestionToAll(currentQuestion);
                    currentQuestion++;
                } else {
                    endGame();
                    questionTimer.cancel();
                }
            }
        }, 0, 15000);
    }

    public synchronized void addClient(ClientThread client) {
        clients.add(client);
        scores.putIfAbsent(client.getClientId(), 0);
    }

    public synchronized void removeClient(ClientThread client) {
        clients.remove(client);
        System.out.println("Client " + client.getClientId() + " disconnected");
    }

    private void sendQuestionToAll(int questionNum) {
        String question = questionPool.getQuestion(questionNum);
        // Only the actual question, no double "QUESTION:" prefix
        System.out.println("[SERVER] Sending Question #" + questionNum + " to all clients");
        System.out.println("----------------------------------------");
        System.out.println(question); // Print the actual question being sent
        System.out.println("----------------------------------------");
    
        questionActive = true;
        buzzQueue.clear();
    
        clients.forEach(client -> {
            try {
                // Sending question with the correct format
                client.sendMessage("QUESTION:" + question); // Ensure only one "QUESTION:" prefix
                System.out.println("[SERVER] Sent to client " + client.getClientId());
            } catch (IOException e) {
                System.err.println("[SERVER] Error sending to client: " + e.getMessage());
            }
        });
    }


    public void processBuzz(BuzzMessage buzz) {
        if (!questionActive) return;

        buzzQueue.add(buzz);
        if (buzzQueue.size() == 1) {
            clients.forEach(client -> {
                try {
                    if (client.getClientId().equals(buzz.getClientId())) {
                        client.sendMessage("ACK");
                        System.out.println("ACK sent to client " + buzz.getClientId());
                    } else {
                        client.sendMessage("NACK");
                    }
                } catch (IOException e) {
                    System.err.println("Error sending response: " + e.getMessage());
                }
            });
        }
    }

    public void processAnswer(String clientId, int answer) {
        boolean isCorrect = questionPool.checkAnswer(currentQuestion - 1, answer);
        int points = isCorrect ? 10 : -10;
        scores.merge(clientId, points, Integer::sum);

        clients.forEach(client -> {
            try {
                if (client.getClientId().equals(clientId)) {
                    client.sendMessage(isCorrect ? "CORRECT" : "WRONG");
                }
                client.sendMessage("SCORE:" + scores.get(clientId));
            } catch (IOException e) {
                System.err.println("Error sending result: " + e.getMessage());
            }
        });
    }

    private void endGame() {
        System.out.println("Game over! Final scores:");
        scores.forEach((id, score) -> 
            System.out.println("Client " + id + ": " + score + " points"));
        
        clients.forEach(client -> {
            try {
                client.sendMessage("GAME_OVER");
            } catch (IOException e) {
                System.err.println("Error sending game over: " + e.getMessage());
            }
        });
    }

    private class UDPListener implements Runnable {
        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    
                    if (message.startsWith("BUZZ:")) {
                        String clientId = message.substring(5);
                        processBuzz(new BuzzMessage(
                            packet.getAddress(), 
                            packet.getPort(), 
                            clientId
                        ));
                    }
                } catch (IOException e) {
                    System.err.println("UDP error: " + e.getMessage());
                }
            }
        }
    }
}