package client;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class ClientWindow implements ActionListener {
    // GUI Components
    private final JFrame window;
    private final JLabel questionLabel, timerLabel, scoreLabel;
    private final JRadioButton[] options;
    private final ButtonGroup optionGroup;
    private final JButton pollButton, submitButton;
    
    // Networking
    private Socket tcpSocket;
    private DatagramSocket udpSocket;
    private PrintWriter out;
    private BufferedReader in;
    private InetAddress serverAddress;
    private int clientId;
    
    // Game State
    private int score = 0;
    private javax.swing.Timer questionTimer;
    private boolean canAnswer = false;
    private int currentQuestion = 1;

    public ClientWindow() {
        window = new JFrame();
        questionLabel = new JLabel("Loading configuration...", SwingConstants.CENTER);
        timerLabel = new JLabel("", SwingConstants.CENTER);
        scoreLabel = new JLabel("Score: 0");
        options = new JRadioButton[4];
        optionGroup = new ButtonGroup();
        pollButton = new JButton("Buzz In!");
        submitButton = new JButton("Submit");
        
        initializeGUI();
        loadConfiguration();
    }

    private void initializeGUI() {
        window.setSize(500, 400);
        window.setLayout(null);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Question label
        questionLabel.setBounds(50, 20, 400, 30);
        window.add(questionLabel);

        // Answer options
        for (int i = 0; i < options.length; i++) {
            options[i] = new JRadioButton();
            options[i].setBounds(50, 60 + (i * 30), 400, 25);
            // Initially, disable options until buzz in
            options[i].setEnabled(false);
            window.add(options[i]);
            optionGroup.add(options[i]);
        }

        // Score label
        scoreLabel.setBounds(50, 200, 100, 30);
        window.add(scoreLabel);

        // Timer label
        timerLabel.setBounds(400, 200, 50, 30);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        window.add(timerLabel);

        // Buzz button (poll)
        pollButton.setBounds(50, 250, 150, 40);
        pollButton.addActionListener(this);
        // Enable poll button on a new question
        pollButton.setEnabled(true);
        window.add(pollButton);

        // Submit button
        submitButton.setBounds(250, 250, 150, 40);
        submitButton.addActionListener(this);
        // Initially disabled until buzz in ACK is received.
        submitButton.setEnabled(false);
        window.add(submitButton);

        window.setVisible(true);
    }

    private void loadConfiguration() {
        Properties config = new Properties();
        
        try (InputStream input = getClass().getResourceAsStream("config.properties")) {
            if (input == null) {
                String errorMsg = "config.properties not found in client package.\n" +
                                  "Expected location: " + new File("src/client/config.properties").getAbsolutePath() +
                                  "\n\nPlease ensure:\n1. File exists\n2. File is named 'config.properties'\n3. It is copied to the output directory.";
                throw new IOException(errorMsg);
            }
    
            config.load(input);
            String clientIdStr = config.getProperty("client.id");
            this.clientId = (clientIdStr != null && !clientIdStr.isEmpty()) 
                ? Integer.parseInt(clientIdStr) 
                : new Random().nextInt(1000) + 1;
            window.setTitle("Trivia Client - ID: " + clientId);
            
            String serverIp = config.getProperty("server.ip", "localhost");
            int tcpPort = Integer.parseInt(config.getProperty("server.tcp.port", "6000"));
            int udpPort = Integer.parseInt(config.getProperty("server.udp.port", "5000"));
            
            this.serverAddress = InetAddress.getByName(serverIp);
            this.tcpSocket = new Socket(serverAddress, tcpPort);
            this.udpSocket = new DatagramSocket();
            
            this.out = new PrintWriter(tcpSocket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            
            out.println("CLIENT_ID:" + clientId);
            new Thread(this::listenForServerMessages).start();
            
            updateStatus("Connected to server at " + serverIp);
            pollButton.setEnabled(true);
            
        } catch (NumberFormatException e) {
            handleConfigError("Invalid number in configuration: " + e.getMessage());
        } catch (UnknownHostException e) {
            handleConfigError("Unknown server address: " + e.getMessage());
        } catch (SocketException e) {
            handleConfigError("Network error: " + e.getMessage());
        } catch (IOException e) {
            handleConfigError("Failed to load configuration: " + e.getMessage());
        } catch (Exception e) {
            handleConfigError("Unexpected error: " + e.getMessage());
        }
    }
    
    private void handleConfigError(String message) {
        String fullMessage = message + "\n\nCurrent working directory: " + System.getProperty("user.dir") +
                             "\n\nApplication will exit.";
        JOptionPane.showMessageDialog(window, fullMessage, "Configuration Error", JOptionPane.ERROR_MESSAGE);
        window.dispose();
        System.exit(1);
    }

    private void listenForServerMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Received: " + message);
                
                if (message.startsWith("QUESTION:")) {
                    handleQuestion(message);
                } else if (message.equals("ACK")) {
                    enableAnswering();
                } else if (message.equals("DISCONNECT")) {
                    updateStatus("Disconnected");
                    pollButton.setEnabled(false);
                    submitButton.setEnabled(false);
                    // Optionally, break out of the loop.
                    break;
                } else if (message.startsWith("SCORE:")) {
                    updateScore(Integer.parseInt(message.substring(6)));
                } else if (message.equals("CORRECT")) {
                    updateStatus("Correct! +10 points");
                } else if (message.equals("WRONG")) {
                    updateStatus("Wrong answer! -10 points");
                } else if (message.equals("NOANSWER")) {
                    updateStatus("No answer submitted. -20 points");
                } else if (message.equals("GAME_OVER")) {
                    showFinalResults();
                }
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                updateStatus("Disconnected from server");
                pollButton.setEnabled(false);
                submitButton.setEnabled(false);
            });
        }
    }

    // Updated handleQuestion:
    // The protocol remains that the server sends a single line encoded with "|||":
    // Format: "QUESTION:<questionNumber>|||<Question Text>|||<Option A>|||<Option B>|||<Option C>|||<Option D>"
    private void handleQuestion(String questionData) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Copy the parameter into a final variable for debugging (do not modify that)
                final String originalData = questionData;
                System.out.println("DEBUG: Raw questionData: \"" + originalData + "\"");
                
                if (originalData == null || originalData.trim().isEmpty()) {
                    updateStatus("Error: Received empty question data");
                    return;
                }
                
                // Create a mutable copy of the parameter so we can modify it.
                String data = originalData;
                // Remove the prefix "QUESTION:" if present.
                if (data.startsWith("QUESTION:")) {
                    data = data.substring("QUESTION:".length());
                }
                // Split using the delimiter "|||"
                String[] parts = data.split("\\|\\|\\|");
                if (parts.length < 6) {
                    updateStatus("Error: Incomplete question data received");
                    System.err.println("DEBUG: parts.length = " + parts.length);
                    return;
                }
                int qNum = Integer.parseInt(parts[0].trim());
                String qText = parts[1].trim();
                questionLabel.setText("Q" + qNum + ": " + qText);
                
                // Display the four options but keep them disabled until you buzz in.
                for (int i = 0; i < 4; i++) {
                    String option = parts[i + 2].trim();
                    options[i].setText(option);
                    options[i].setEnabled(false);
                    options[i].setSelected(false);
                }
                
                // When a new question is sent:
                // - Enable the poll (buzz in) button.
                // - Disable the submit button until buzzing in.
                // - Disable the answer options until buzzing in.
                startTimer(30);
                pollButton.setEnabled(true);
                submitButton.setEnabled(false);
                currentQuestion = qNum + 1;
            } catch(Exception e) {
                updateStatus("Error loading question");
                System.err.println("Question display error: " + e.getMessage());
            }
        });
    }

    // enableAnswering is only called after the server sends an "ACK" message in response to your buzz.
    // This method enables the answer options and submit button.
    private void enableAnswering() {
        SwingUtilities.invokeLater(() -> {
            canAnswer = true;
            for (JRadioButton option : options) {
                option.setEnabled(true);
            }
            submitButton.setEnabled(true);
            // You might also want to adjust the timer for answering (e.g., shorten it to 10 seconds)
            startTimer(10);
        });
    }

    // submitAnswer sends the answer to the server and then disables interaction until next question.
    private void submitAnswer(int answer) {
        if (answer == -1) {
            System.out.println("DEBUG: No answer selected, skipping answer submission.");
            return;
        }
        out.println("ANSWER:" + answer);
        System.out.println("DEBUG: Answer submitted: " + answer);
        canAnswer = false;
        SwingUtilities.invokeLater(() -> {
            // Disable the submit button and answer options until the next question
            submitButton.setEnabled(false);
            for (JRadioButton option : options) {
                option.setEnabled(false);
            }
            // Also disable the poll button in case it hasn't been re-enabled.
            pollButton.setEnabled(false);
        });
    }

    private void startTimer(int seconds) {
        if (questionTimer != null) {
            questionTimer.stop();
        }
        final int[] timeLeft = {seconds};
        questionTimer = new javax.swing.Timer(1000, e -> {
            timeLeft[0]--;
            SwingUtilities.invokeLater(() -> {
                timerLabel.setText(String.valueOf(timeLeft[0]));
                timerLabel.setForeground(timeLeft[0] <= 5 ? Color.RED : Color.BLACK);
            });
            if (timeLeft[0] <= 0) {
                questionTimer.stop();
                if (canAnswer) {
                    submitAnswer(-1);
                }
            }
        });
        questionTimer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == pollButton) {
            try {
                byte[] data = ("BUZZ:" + clientId).getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, 5000);
                udpSocket.send(packet);
                // Disable the poll button immediately after buzzing in.
                SwingUtilities.invokeLater(() -> pollButton.setEnabled(false));
            } catch (IOException ex) {
                updateStatus("Error sending buzz");
            }
        } else if (e.getSource() == submitButton) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].isSelected()) {
                    submitAnswer(i + 1);
                    return;
                }
            }
            // If no option is selected, treat it as a missed answer.
            submitAnswer(-1);
        }
    }

    public void updateScore(int newScore) {
        SwingUtilities.invokeLater(() -> {
            score = newScore;
            scoreLabel.setText("Score: " + score);
        });
    }

    public void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            questionLabel.setText(message);
        });
    }

    private void showFinalResults() {
        SwingUtilities.invokeLater(() -> {
            questionLabel.setText("Game Over! Final Score: " + score);
            pollButton.setEnabled(false);
            submitButton.setEnabled(false);
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientWindow());
    }
}