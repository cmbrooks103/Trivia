package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

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
        // Initialize GUI first
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

        // Question Label
        questionLabel.setBounds(50, 20, 400, 30);
        window.add(questionLabel);

        // Options
        for (int i = 0; i < options.length; i++) {
            options[i] = new JRadioButton();
            options[i].setBounds(50, 60 + (i * 30), 400, 25);
            options[i].setEnabled(false);
            window.add(options[i]);
            optionGroup.add(options[i]);
        }

        // Score
        scoreLabel.setBounds(50, 200, 100, 30);
        window.add(scoreLabel);

        // Timer
        timerLabel.setBounds(400, 200, 50, 30);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        window.add(timerLabel);

        // Buttons
        pollButton.setBounds(50, 250, 150, 40);
        pollButton.addActionListener(this);
        pollButton.setEnabled(false);
        window.add(pollButton);

        submitButton.setBounds(250, 250, 150, 40);
        submitButton.addActionListener(this);
        submitButton.setEnabled(false);
        window.add(submitButton);

        window.setVisible(true);
    }

    private void loadConfiguration() {
        Properties config = new Properties();
        
        try (InputStream input = getClass().getResourceAsStream("config.properties")) {
            // Verify file exists
            if (input == null) {
                String errorMsg = "config.properties not found in client package.\n" +
                               "Expected location: " + 
                               new File("src/client/config.properties").getAbsolutePath() +
                               "\n\nPlease ensure:";
                errorMsg += "\n1. The file exists in src/client/ folder";
                errorMsg += "\n2. The file is named exactly 'config.properties'";
                errorMsg += "\n3. For IDE builds, the file is copied to output directory";
                
                throw new IOException(errorMsg);
            }
    
            // Load properties
            config.load(input);
            
            // Set client ID (from config or random)
            String clientIdStr = config.getProperty("client.id");
            this.clientId = (clientIdStr != null && !clientIdStr.isEmpty()) 
                ? Integer.parseInt(clientIdStr) 
                : new Random().nextInt(1000) + 1;
            window.setTitle("Trivia Client - ID: " + clientId);
            
            // Get server connection details
            String serverIp = config.getProperty("server.ip", "localhost");
            int tcpPort = Integer.parseInt(config.getProperty("server.tcp.port", "6000"));
            int udpPort = Integer.parseInt(config.getProperty("server.udp.port", "5000"));
            
            // Establish TCP connection
            this.serverAddress = InetAddress.getByName(serverIp);
            this.tcpSocket = new Socket(serverAddress, tcpPort);
            this.udpSocket = new DatagramSocket();
            
            // Initialize streams
            this.out = new PrintWriter(tcpSocket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            
            // Register with server
            out.println("CLIENT_ID:" + clientId);
            new Thread(this::listenForServerMessages).start();
            
            // Update UI
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
        // Show detailed error dialog
        String fullMessage = message + "\n\nCurrent working directory: " + 
                            System.getProperty("user.dir") +
                            "\n\nApplication will exit.";
        
        JOptionPane.showMessageDialog(window, 
            fullMessage,
            "Configuration Error", 
            JOptionPane.ERROR_MESSAGE);
        
        // Close window gracefully
        window.dispose();
        System.exit(1);
    }

    private void listenForServerMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Received: " + message);
                
                if (message.startsWith("QUESTION:")) {
                    handleQuestion(message.substring(9));
                }
                else if (message.equals("ACK")) {
                    enableAnswering();
                }
                else if (message.startsWith("SCORE:")) {
                    updateScore(Integer.parseInt(message.substring(6)));
                }
                else if (message.equals("CORRECT")) {
                    updateStatus("Correct! +10 points");
                }
                else if (message.equals("WRONG")) {
                    updateStatus("Wrong answer! -10 points");
                }
                else if (message.equals("GAME_OVER")) {
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

    private void handleQuestion(String questionData) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Reset UI state
                questionLabel.setText("Loading question...");
                for (JRadioButton option : options) {
                    option.setText("");
                    option.setSelected(false);
                    option.setEnabled(false);
                }
    
                // Parse and display question
                String[] parts = questionData.split("\n");
                String qText = parts[0].replace("QUESTION:", "").trim();
                System.out.println("DEBUG: Parsed Question Text: " + qText);
                questionLabel.setText("Q" + currentQuestion + ": " + qText);
    
                // Display options
                for (int i = 0; i < 4 && (i + 1) < parts.length; i++) {
                    String optionText = parts[i + 1].substring(parts[i + 1].indexOf(":") + 1).trim();
                    System.out.println("DEBUG: Option " + (i + 1) + ": " + optionText);
                    options[i].setText(optionText);
                    options[i].setEnabled(true);
                }
    
                // Start the timer and enable the buttons for answering
                startTimer(30);
                pollButton.setEnabled(true);
                submitButton.setEnabled(true);
                currentQuestion++;
            } catch (Exception e) {
                updateStatus("Error loading question");
                System.err.println("Question display error: " + e.getMessage());
            }
        });
    }

    private void enableAnswering() {
        SwingUtilities.invokeLater(() -> {
            canAnswer = true;
            for (JRadioButton option : options) {
                option.setEnabled(true);
            }
            submitButton.setEnabled(true);
            startTimer(10);
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

    private void submitAnswer(int answer) {
        if (answer == -1) {
            System.out.println("DEBUG: No answer selected, skipping answer submission.");
            return;
        }
    
        out.println("ANSWER:" + answer);
        System.out.println("DEBUG: Answer submitted: " + answer);
    
        canAnswer = false;
    
        SwingUtilities.invokeLater(() -> {
            submitButton.setEnabled(false);
            for (JRadioButton option : options) {
                option.setEnabled(false);
            }
        });
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

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == pollButton) {
            try {
                byte[] data = ("BUZZ:" + clientId).getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, 5000);
                udpSocket.send(packet);
                SwingUtilities.invokeLater(() -> pollButton.setEnabled(false));
            } catch (IOException ex) {
                updateStatus("Error sending buzz");
            }
        } 
        else if (e.getSource() == submitButton) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].isSelected()) {
                    submitAnswer(i + 1);
                    return;
                }
            }
            submitAnswer(-1);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ClientWindow();
        });
    }
}