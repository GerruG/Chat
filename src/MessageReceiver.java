import java.io.IOException;
import java.net.DatagramPacket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageReceiver implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(MessageReceiver.class.getName());
    private final MulticastNetworking networking;
    private final ChatClient client;
    private volatile boolean running = true;

    public MessageReceiver(MulticastNetworking networking, ChatClient client) {
        this.networking = networking;
        this.client = client;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                networking.getSocket().receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received packet: " + message); // Add logging here

                String[] parts = message.split(":", 3);

                // Adjust parsing and validation
                if (parts.length < 2) {
                    System.out.println("Received malformed message: " + message);
                    continue;
                }

                switch (parts[0]) {
                    case "MESSAGE" -> {
                        if (parts.length == 3) {
                            String user = parts[1];
                            String msg = parts[2];
                            client.displayMessage(user + ": " + msg);
                        } else {
                            System.out.println("Received malformed message: " + message);
                        }
                    }
                    case "JOIN" -> {
                        String newUser = parts[1];
                        client.getUsers().add(newUser);
                        client.updateUserList(client.getUsers());
                        client.displayMessage(newUser + " has joined the chat.");
                    }
                    case "LEAVE" -> {
                        String leavingUser = parts[1];
                        client.getUsers().remove(leavingUser);
                        client.updateUserList(client.getUsers());
                        client.displayMessage(leavingUser + " has left the chat.");
                    }
                    case "REQUEST_USER_LIST" -> {
                        String requester = parts[1];
                        networking.sendUserList(requester, client.getUsers());
                    }
                    case "USER_LIST" -> {
                        if (parts.length == 3) {
                            String user = parts[2];
                            if (!client.getUsers().contains(user)) {
                                client.getUsers().add(user);
                                client.updateUserList(client.getUsers());
                            }
                        } else {
                            System.out.println("Received malformed message: " + message);
                        }
                    }
                    default -> System.out.println("Unknown message type: " + parts[0]);
                }
            } catch (IOException e) {
                if (!running) {
                    System.out.println("Socket closed, stopping receiver thread.");
                } else {
                    LOGGER.log(Level.SEVERE, "Error receiving packet: " + e.getMessage(), e);
                }
                break;
            }
        }
    }

    public void stopRunning() {
        running = false;
    }
}
