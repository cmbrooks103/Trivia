package server;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;

public class UDPThread implements Runnable {
    private DatagramSocket socket;
    private ConcurrentLinkedQueue<BuzzMessage> buzzQueue;

    public UDPThread(DatagramSocket socket, ConcurrentLinkedQueue<BuzzMessage> queue) {
        this.socket = socket;
        this.buzzQueue = queue;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String buzzMsg = new String(packet.getData(), 0, packet.getLength());
                buzzQueue.add(new BuzzMessage(packet.getAddress(), packet.getPort(), buzzMsg));
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
}