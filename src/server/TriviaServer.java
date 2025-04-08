package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TriviaServer {
    private ServerSocket tcpSocket;
    private DatagramSocket udpSocket;
    private ExecutorService threadPool;
    private QuestionPool questionPool;
    private ConcurrentLinkedQueue<BuzzMessage> buzzQueue;
    // Each client's score is maintained individually
    private Map<String, Integer> scores;
    private List<ClientThread> clients;
    
    // currentQuestion holds the number of the next question to send (1-based)
    private int currentQuestion = 1;
    // activeQuestion holds the currently active question number (1-based)
    private int activeQuestion = 0;
    
    // This set tracks client IDs that submitted an answer for a given question (not used for penalty anymore)
    private Set<String> answeredClients = new HashSet<>();
    
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

            // Start UDP listener for buzz messages
            new Thread(new UDPListener()).start();

            // Start the question timer thread.
            startQuestionTimer();

            // (Optional) Command thread to kill clients remains intact
            new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                while (scanner.hasNextLine()) {
                    String command = scanner.nextLine().trim();
                    if (command.startsWith("kill")) {
                        String clientIdToKill = command.substring(4).trim();
                        killClient(clientIdToKill);
                    }
                }
            }).start();

            // Accept incoming TCP connections.
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
        // Schedule a new question every 15 seconds.
        questionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Removed penalty for unanswered question.
                // Simply clear the answeredClients set for the next round.
                answeredClients.clear();
                
                if (currentQuestion <= questionPool.getQuestionCount()) {
                    sendQuestionToAll(currentQuestion);
                } else {
                    endGame();
                    questionTimer.cancel();
                }
            }
        }, 0, 15000);
    }

    // Sends the question to all connected clients.
    private void sendQuestionToAll(int questionNum) {
        activeQuestion = questionNum;
        String question = questionPool.getQuestion(questionNum);
        System.out.println("[SERVER] Sending Question #" + questionNum);
        System.out.println("----------------------------------------");
        System.out.println(question);
        System.out.println("----------------------------------------");

        questionActive = true;
        buzzQueue.clear();
        answeredClients.clear();

        // Encode the question text: replace newlines with "|||"
        String encodedQuestion = question.replace("\n", "|||");
        // Message format: "QUESTION:<questionNumber>|||<Question Text>|||<Option A>|||<Option B>|||<Option C>|||<Option D>"
        String message = "QUESTION:" + questionNum + "|||" + encodedQuestion;
        
        clients.forEach(client -> {
            try {
                client.sendMessage(message);
                System.out.println("[SERVER] Sent to client " + client.getClientId());
            } catch (IOException e) {
                System.err.println("[SERVER] Error sending to client: " + e.getMessage());
            }
        });
        currentQuestion++;
    }

    // When a new client connects, send it the current active question if one is in progress.
    public synchronized void addClient(ClientThread client) {
        clients.add(client);
        scores.putIfAbsent(client.getClientId(), 0);

        if (questionActive && currentQuestion > 1) {  // There is an active question.
            try {
                String question = questionPool.getQuestion(activeQuestion);
                String encodedQuestion = question.replace("\n", "|||");
                client.sendMessage("QUESTION:" + activeQuestion + "|||" + encodedQuestion);
            } catch(IOException e) {
                System.err.println("Error sending current question to new client: " + e.getMessage());
            }
        }
    }

    public synchronized void removeClient(ClientThread client) {
        clients.remove(client);
        System.out.println("Client " + client.getClientId() + " disconnected");
    }

    // Modified processAnswer: If a client submits an answer, process it normally.
    // No extra penalty is applied if no answer is submitted.
    public void processAnswer(String clientId, int answer) {
        answeredClients.add(clientId);
        boolean isCorrect = questionPool.checkAnswer(activeQuestion - 1, answer);
        int points = isCorrect ? 10 : -10;
        scores.merge(clientId, points, Integer::sum);

        // Send result only to the answering client.
        for (ClientThread client : clients) {
            if (client.getClientId().equals(clientId)) {
                try {
                    client.sendMessage(isCorrect ? "CORRECT" : "WRONG");
                    client.sendMessage("SCORE:" + scores.get(clientId));
                } catch (IOException e) {
                    System.err.println("Error sending result: " + e.getMessage());
                }
                break;
            }
        }
    }

    private void endGame() {
        System.out.println("Game over! Final scores:");
        // Determine the winner: the client with the maximum score.
        String winnerId = null;
        int maxScore = Integer.MIN_VALUE;
        
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                winnerId = entry.getKey();
            }
        }
        
        // Sort the leaderboard by score descending.
        List<Map.Entry<String, Integer>> leaderboard = new ArrayList<>(scores.entrySet());
        leaderboard.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));
        
        // Print the winner.
        if (winnerId != null) {
            System.out.println("Winner Client" + winnerId + ": with " + maxScore + " points!");
        }
        System.out.println("Leaderboard:");
        for (Map.Entry<String, Integer> entry : leaderboard) {
            System.out.println("Client " + entry.getKey() + ": " + entry.getValue() + " points");
        }
        
        // Notify each client of the game over.
        clients.forEach(client -> {
            try {
                client.sendMessage("GAME_OVER");
            } catch (IOException e) {
                System.err.println("Error sending game over: " + e.getMessage());
            }
        });
    }

    // Kill a client by its id, called when a command "killX" is entered.
    private void killClient(String clientId) {
        System.out.println("[SERVER] Kill command received for client id: " + clientId);
        for (ClientThread client : clients) {
            if (client.getClientId().equals(clientId)) {
                try {
                    client.sendMessage("DISCONNECT");
                } catch (IOException e) {
                    System.err.println("Error sending disconnect message: " + e.getMessage());
                }
                client.kill();
                removeClient(client);
                System.out.println("[SERVER] Killed client " + clientId);
                break;
            }
        }
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
                        processBuzz(new BuzzMessage(packet.getAddress(), packet.getPort(), clientId));
                    }
                } catch (IOException e) {
                    System.err.println("UDP error: " + e.getMessage());
                }
            }
        }
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
                    System.err.println("Error sending buzz response: " + e.getMessage());
                }
            });
        }
    }
}