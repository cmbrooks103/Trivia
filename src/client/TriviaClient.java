package client;

import java.io.IOException;
import java.net.*;
import javax.swing.*;

public class TriviaClient {
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