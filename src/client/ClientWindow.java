package client;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

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
    window.setLayout(null);  // Using null layout for absolute positioning
    window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // Question Label
    questionLabel.setBounds(50, 20, 400, 30);
    questionLabel.setBorder(BorderFactory.createLineBorder(Color.RED));  // Debug border
    window.add(questionLabel);

    // Options
// In initializeGUI():
for (int i = 0; i < options.length; i++) {
    options[i] = new JRadioButton();
    options[i].setBounds(50, 60 + (i * 40), 400, 30); // Increased height and spacing
    options[i].setFont(new Font("Arial", Font.PLAIN, 14));
    options[i].setForeground(Color.BLACK); // <-- THIS IS THE NEW LINE FOR FONT COLOR
    options[i].setBackground(Color.WHITE); // Make sure background is visible
    options[i].setOpaque(true); // Required for background color
    options[i].setBorder(BorderFactory.createLineBorder(Color.BLUE));  // Debug border
    window.add(options[i]);
    optionGroup.add(options[i]);
}

    // Other components...
    scoreLabel.setBounds(50, 200, 100, 30);
    scoreLabel.setBorder(BorderFactory.createLineBorder(Color.GREEN));  // Debug border
    window.add(scoreLabel);

    // Timer and buttons...
    timerLabel.setBounds(400, 200, 50, 30);
    timerLabel.setBorder(BorderFactory.createLineBorder(Color.MAGENTA));  // Debug border
    window.add(timerLabel);

    // Make window visible last
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
                    // Handle multi-line question
                    StringBuilder fullQuestion = new StringBuilder(message);
                    // Read additional lines until empty line
                    while (!(message = in.readLine()).isEmpty()) {
                        fullQuestion.append("\n").append(message);
                    }
                    handleQuestion(fullQuestion.toString().substring(9)); // Remove "QUESTION:"
                }
                // ... rest of your existing message handling ...
            }
        } catch (IOException e) {
            // ... error handling ...
        }
    }

    private void handleQuestion(String questionData) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 1. Debug raw input
                System.out.println("=== RAW QUESTION ===");
                System.out.println(questionData);
    
                // 2. Normalize line endings and split
                String normalized = questionData.replace("\r", "");
                String[] lines = normalized.split("\n");
                
                // 3. Process each line
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
    
                    if (line.startsWith("QUESTION:")) {
                        String qText = line.substring("QUESTION:".length()).trim();
                        questionLabel.setText("Q" + currentQuestion + ": " + qText);
                    } 
                    // Handle options explicitly
                    else if (line.startsWith("OPTION_A:")) {
                        options[0].setText(line.substring("OPTION_A:".length()).trim());
                        options[0].setEnabled(true);
                    } 
                    else if (line.startsWith("OPTION_B:")) {
                        options[1].setText(line.substring("OPTION_B:".length()).trim());
                        options[1].setEnabled(true);
                    }
                    else if (line.startsWith("OPTION_C:")) {
                        options[2].setText(line.substring("OPTION_C:".length()).trim());
                        options[2].setEnabled(true);
                    }
                    else if (line.startsWith("OPTION_D:")) {
                        options[3].setText(line.substring("OPTION_D:".length()).trim());
                        options[3].setEnabled(true);
                    }
                }
    
                // Debug final option states
                System.out.println("=== FINAL OPTIONS ===");
                for (int i = 0; i < options.length; i++) {
                    System.out.printf("Option %d: '%s' (enabled: %b)\n", 
                        i, options[i].getText(), options[i].isEnabled());
                }
    
                startTimer(30);
                pollButton.setEnabled(true);
                submitButton.setEnabled(true);
                currentQuestion++;
    
            } catch (Exception e) {
                updateStatus("Error loading question");
                e.printStackTrace();
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