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
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.Random;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;

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

    // Audio
    private Clip backgroundMusic;

    // Colors
    private final Color YELLOW_COLOR = new Color(255, 255, 128); // Light yellow
    private final Color BLUE_COLOR = new Color(0, 149, 237);  // Light blue
    private final Color BLUE_BORDER = new Color(0, 0, 139);     // Dark blue for borders

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
        window.getContentPane().setBackground(YELLOW_COLOR);

        // Add window listeners for music control
        window.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            public void windowGainedFocus(java.awt.event.WindowEvent evt) {
                playBackgroundMusic();
            }
            public void windowLostFocus(java.awt.event.WindowEvent evt) {
                pauseBackgroundMusic();
            }
        });

        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                stopBackgroundMusic();
            }
        });

        // Question label
        questionLabel.setBounds(50, 20, 400, 30);
        questionLabel.setOpaque(true);
        questionLabel.setBackground(BLUE_COLOR);
        questionLabel.setBorder(new LineBorder(BLUE_BORDER, 2));
        window.add(questionLabel);

        // Answer options
        for (int i = 0; i < options.length; i++) {
            options[i] = new JRadioButton();
            options[i].setBounds(50, 60 + (i * 30), 400, 25);
            options[i].setEnabled(false);
            options[i].setOpaque(true);
            options[i].setBackground(BLUE_COLOR);
            options[i].setBorder(new LineBorder(BLUE_BORDER, 1));
            window.add(options[i]);
            optionGroup.add(options[i]);
        }

        // Score label
        scoreLabel.setBounds(50, 200, 100, 30);
        scoreLabel.setOpaque(true);
        scoreLabel.setBackground(BLUE_COLOR);
        scoreLabel.setBorder(new LineBorder(BLUE_BORDER, 1));
        window.add(scoreLabel);

        // Timer label
        timerLabel.setBounds(400, 200, 50, 30);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        timerLabel.setOpaque(true);
        timerLabel.setBackground(BLUE_COLOR);
        timerLabel.setBorder(new LineBorder(BLUE_BORDER, 1));
        window.add(timerLabel);

        // Buzz button (poll)
        pollButton.setBounds(50, 250, 150, 40);
        pollButton.addActionListener(this);
        pollButton.setEnabled(true);
        pollButton.setBackground(BLUE_COLOR);
        pollButton.setBorder(new LineBorder(BLUE_BORDER, 2));
        pollButton.setForeground(Color.BLACK);
        window.add(pollButton);

        // Submit button
        submitButton.setBounds(250, 250, 150, 40);
        submitButton.addActionListener(this);
        submitButton.setEnabled(false);
        submitButton.setBackground(BLUE_COLOR);
        submitButton.setBorder(new LineBorder(BLUE_BORDER, 2));
        submitButton.setForeground(Color.BLACK);
        window.add(submitButton);

        window.setVisible(true);
        playBackgroundMusic(); // Start music when window opens
    }

    private void playBackgroundMusic() {
        try {
            // Stop any currently playing music
            if (backgroundMusic != null && backgroundMusic.isRunning()) {
                backgroundMusic.stop();
            }
            
            // Load the audio file from the client package
            URL url = getClass().getResource("song.wav");
            if (url == null) {
                System.err.println("Could not find song.wav in client package");
                return;
            }
            
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(url);
            backgroundMusic = AudioSystem.getClip();
            backgroundMusic.open(audioIn);
            backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
            backgroundMusic.start();
        } catch (Exception e) {
            System.err.println("Error playing background music: " + e.getMessage());
        }
    }

    private void pauseBackgroundMusic() {
        if (backgroundMusic != null && backgroundMusic.isRunning()) {
            backgroundMusic.stop();
        }
    }

    private void stopBackgroundMusic() {
        if (backgroundMusic != null) {
            backgroundMusic.stop();
            backgroundMusic.close();
        }
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

    private void handleQuestion(String questionData) {
        SwingUtilities.invokeLater(() -> {
            try {
                final String originalData = questionData;
                System.out.println("DEBUG: Raw questionData: \"" + originalData + "\"");
                
                if (originalData == null || originalData.trim().isEmpty()) {
                    updateStatus("Error: Received empty question data");
                    return;
                }
                
                String data = originalData;
                if (data.startsWith("QUESTION:")) {
                    data = data.substring("QUESTION:".length());
                }
                String[] parts = data.split("\\|\\|\\|");
                if (parts.length < 6) {
                    updateStatus("Error: Incomplete question data received");
                    System.err.println("DEBUG: parts.length = " + parts.length);
                    return;
                }
                int qNum = Integer.parseInt(parts[0].trim());
                String qText = parts[1].trim();
                questionLabel.setText("Q" + qNum + ": " + qText);
                
                for (int i = 0; i < 4; i++) {
                    String option = parts[i + 2].trim();
                    options[i].setText(option);
                    options[i].setEnabled(false);
                    options[i].setSelected(false);
                }
                
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
            stopBackgroundMusic();
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientWindow());
    }
}