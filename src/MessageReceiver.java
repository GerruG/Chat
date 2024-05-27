import java.io.IOException;
import java.net.DatagramPacket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageReceiver implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(MessageReceiver.class.getName());
    private final MulticastNetworking networking;
    private final ChatClient client;
    private volatile boolean running = true;

    /**
     * Constructor for MessageReceiver.
     *
     * @param networking the multicast networking instance
     * @param client     the chat client instance
     */
    public MessageReceiver(MulticastNetworking networking, ChatClient client) {
        this.networking = networking;
        this.client = client;
    }

    /**
     * The run method that continuously listens for incoming packets
     * and processes them based on the message type.
     */
    @Override
    public void run() {
        byte[] buffer = new byte[1024]; // Buffer to hold incoming packets
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                networking.getSocket().receive(packet); // Receive packet
                String message = new String(packet.getData(), 0, packet.getLength());
                LOGGER.info("Received packet: " + message); // Log received message

                String[] parts = message.split(":", 3); // Split the message into parts

                // Check if the message is well-formed
                if (parts.length < 2) {
                    LOGGER.warning("Received malformed message: " + message); // Log malformed message
                    continue;
                }

                switch (parts[0]) {
                    case "MESSAGE" -> {
                        // Handle a chat message
                        if (parts.length == 3) {
                            String user = parts[1];
                            String msg = parts[2];
                            client.displayMessage(user + ": " + msg); // Display the message in the chat client
                        } else {
                            LOGGER.warning("Received malformed message: " + message); // Log malformed message
                        }
                    }
                    case "JOIN" -> {
                        // Handle a join message
                        String newUser = parts[1];
                        client.getUsers().add(newUser); // Add new user to the list
                        client.updateUserList(client.getUsers()); // Update the user list in the chat client
                        client.displayMessage(newUser + " has joined the chat."); // Display join message
                    }
                    case "LEAVE" -> {
                        // Handle a leave message
                        String leavingUser = parts[1];
                        client.getUsers().remove(leavingUser); // Remove user from the list
                        client.updateUserList(client.getUsers()); // Update the user list in the chat client
                        client.displayMessage(leavingUser + " has left the chat."); // Display leave message
                    }
                    case "REQUEST_USER_LIST" -> {
                        // Handle a request for the user list
                        String requester = parts[1];
                        networking.sendUserList(requester, client.getUsers()); // Send the user list to the requester
                    }
                    case "USER_LIST" -> {
                        // Handle receiving a user list
                        if (parts.length == 3) {
                            String user = parts[2];
                            if (!client.getUsers().contains(user)) {
                                client.getUsers().add(user); // Add user to the list if not already present
                                client.updateUserList(client.getUsers()); // Update the user list in the chat client
                            }
                        } else {
                            LOGGER.warning("Received malformed message: " + message); // Log malformed message
                        }
                    }
                    default -> LOGGER.warning("Unknown message type: " + parts[0]); // Log unknown message type
                }
            } catch (IOException e) {
                if (!running) {
                    LOGGER.info("Socket closed, stopping receiver thread."); // Log socket closure
                } else {
                    LOGGER.log(Level.SEVERE, "Error receiving packet: " + e.getMessage(), e); // Log error receiving packet
                }
                break;
            }
        }
    }

    /**
     * Stops the receiver thread.
     */
    public void stopRunning() {
        running = false;
    }
}
