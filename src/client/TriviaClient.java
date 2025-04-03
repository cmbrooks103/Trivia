package client;

import javax.swing.*;

public class TriviaClient {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new ClientWindow(); // ClientWindow handles its own initialization
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                    "Client initialization failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}