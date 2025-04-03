package server;

import java.net.*;

public class BuzzMessage {
    private final InetAddress clientAddress;
    private final int clientPort;
    private final String clientId;

    public BuzzMessage(InetAddress address, int port, String clientId) {
        this.clientAddress = address;
        this.clientPort = port;
        this.clientId = clientId;
    }

    public InetAddress getAddress() { return clientAddress; }
    public int getPort() { return clientPort; }
    public String getClientId() { return clientId; }
}