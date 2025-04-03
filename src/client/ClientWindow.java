package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class ClientWindow implements ActionListener {
    // GUI Components
    private JFrame window;
    private JLabel questionLabel, timerLabel, scoreLabel;
    private JRadioButton[] options;
    private ButtonGroup optionGroup;
    private JButton pollButton, submitButton;
    
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
        initializeGUI();
        this.clientId = new Random().nextInt(1000) + 1;
        window.setTitle("Trivia Client - ID: " + clientId);
    }

    public void setNetworkConnections(Socket tcpSocket, DatagramSocket udpSocket, InetAddress serverAddress) {
        this.tcpSocket = tcpSocket;
        this.udpSocket = udpSocket;
        this.serverAddress = serverAddress;
        
        try {
            this.out = new PrintWriter(tcpSocket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            
            out.println("CLIENT_ID:" + clientId);
            new Thread(this::listenForServerMessages).start();
            
            updateStatus("Connected to server!");
            pollButton.setEnabled(true);
        } catch (IOException e) {
            updateStatus("Connection error: " + e.getMessage());
        }
    }

    private void initializeGUI() {
        window = new JFrame();
        window.setSize(500, 400);
        window.setLayout(null);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        questionLabel = new JLabel("Connecting to server...", SwingConstants.CENTER);
        questionLabel.setBounds(50, 20, 400, 30);
        window.add(questionLabel);

        options = new JRadioButton[4];
        optionGroup = new ButtonGroup();
        for (int i = 0; i < options.length; i++) {
            options[i] = new JRadioButton();
            options[i].setBounds(50, 60 + (i * 30), 400, 25);
            options[i].setEnabled(false);
            window.add(options[i]);
            optionGroup.add(options[i]);
        }

        scoreLabel = new JLabel("Score: 0");
        scoreLabel.setBounds(50, 200, 100, 30);
        window.add(scoreLabel);

        timerLabel = new JLabel("15");
        timerLabel.setBounds(400, 200, 50, 30);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        window.add(timerLabel);

        pollButton = new JButton("Buzz In!");
        pollButton.setBounds(50, 250, 150, 40);
        pollButton.addActionListener(this);
        pollButton.setEnabled(false);
        window.add(pollButton);

        submitButton = new JButton("Submit");
        submitButton.setBounds(250, 250, 150, 40);
        submitButton.addActionListener(this);
        submitButton.setEnabled(false);
        window.add(submitButton);

        window.setVisible(true);
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
            updateStatus("Disconnected from server");
        }
    }
    private void handleQuestion(String questionData) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Reset UI state
                questionLabel.setText("Loading question...");
                for (JRadioButton option : options) {
                    option.setText("");  // Reset options
                    option.setSelected(false);
                    option.setEnabled(false);  // Ensure they're initially disabled
                }
    
                // Parse and display question
                String[] parts = questionData.split("\n");  // Split the question data into parts
                String qText = parts[0].replace("QUESTION:", "").trim();  // Extract the question text
                System.out.println("DEBUG: Parsed Question Text: " + qText);  // Debug log for the question text
                questionLabel.setText("Q" + currentQuestion + ": " + qText);
    
                // Display options
                for (int i = 0; i < 4 && (i + 1) < parts.length; i++) {
                    String optionText = parts[i + 1].substring(parts[i + 1].indexOf(":") + 1).trim();
                    System.out.println("DEBUG: Option " + (i + 1) + ": " + optionText);  // Debug log for each option
                    options[i].setText(optionText);  // Set the option text
                    options[i].setEnabled(true);  // Enable the options after question is loaded
                }
    
                // Start the timer and enable the buttons for answering
                startTimer(30);
                pollButton.setEnabled(true);
                submitButton.setEnabled(true);  // Enable the Submit button as well
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
            return;  // Don't submit if no option is selected
        }
    
        // Send the selected answer only after clicking Submit
        out.println("ANSWER:" + answer);
        System.out.println("DEBUG: Answer submitted: " + answer);  // Log the answer submission
    
        canAnswer = false;  // Prevent further answering until next question
    
        // Disable the submit button and options after answer submission
        SwingUtilities.invokeLater(() -> {
            submitButton.setEnabled(false);
            for (JRadioButton option : options) {
                option.setEnabled(false);  // Disable all options after submitting
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
            ClientWindow window = new ClientWindow();
            try {
                Socket tcpSocket = new Socket("localhost", 6000);
                DatagramSocket udpSocket = new DatagramSocket();
                InetAddress serverAddress = InetAddress.getByName("localhost");
                window.setNetworkConnections(tcpSocket, udpSocket, serverAddress);
            } catch (IOException e) {
                window.updateStatus("Connection failed");
                JOptionPane.showMessageDialog(null, 
                    "Connection failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}