import java.io.IOException;
import java.net.*;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MulticastNetworking {
    private static final Logger LOGGER = Logger.getLogger(MulticastNetworking.class.getName());
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int PORT = 4446;

    private MulticastSocket socket;
    private InetAddress group;
    private final ChatClient client;
    private final MessageReceiver messageReceiver;

    /**
     * Constructor for MulticastNetworking.
     * Sets up networking, starts the message receiver thread, and joins the chat.
     *
     * @param client the chat client
     * @throws IOException if an I/O error occurs
     */
    public MulticastNetworking(ChatClient client) throws IOException {
        this.client = client;
        setupNetworking();
        messageReceiver = new MessageReceiver(this, client);
        new Thread(messageReceiver).start();
        joinChat(client.username);
        requestUserList(client.username);
    }

    /**
     * Sets up the multicast socket and joins the multicast group.
     *
     * @throws IOException if an I/O error occurs
     */
    private void setupNetworking() throws IOException {
        socket = new MulticastSocket(PORT); // Create a multicast socket at the specified port
        group = InetAddress.getByName(MULTICAST_ADDRESS); // Get the multicast group address
        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
        socket.joinGroup(new InetSocketAddress(group, PORT), networkInterface); // Join the multicast group
    }

    /**
     * Joins the chat by sending a join message and updating the user list.
     *
     * @param username the username of the client joining the chat
     */
    public void joinChat(String username) {
        sendPacket("JOIN:" + username); // Send join message
        client.getUsers().add(username); // Add username to the client’s user list
        client.updateUserList(client.getUsers()); // Update the user list on the client side
    }

    /**
     * Leaves the chat by sending a leave message, stopping the message receiver,
     * and leaving the multicast group.
     *
     * @param username the username of the client leaving the chat
     */
    public void leaveChat(String username) {
        messageReceiver.stopRunning(); // Stop the message receiver thread
        sendPacket("LEAVE:" + username); // Send leave message
        client.getUsers().remove(username); // Remove username from the client’s user list
        if (socket != null && !socket.isClosed()) {
            try {
                NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                socket.leaveGroup(new InetSocketAddress(group, PORT), networkInterface); // Leave the multicast group
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error leaving group: " + e.getMessage(), e);
            }
            socket.close(); // Close the socket
        }
    }

    /**
     * Requests the list of users in the chat by sending a request user list message.
     *
     * @param username the username of the client requesting the user list
     */
    public void requestUserList(String username) {
        sendPacket("REQUEST_USER_LIST:" + username); // Send request user list message
    }

    /**
     * Sends the list of users to the requester.
     *
     * @param requester the username of the client requesting the user list
     * @param users     the set of users in the chat
     */
    public void sendUserList(String requester, Set<String> users) {
        for (String user : users) {
            sendPacket("USER_LIST:" + requester + ":" + user); // Send each user in the user list
        }
    }

    /**
     * Sends a packet to the multicast group.
     *
     * @param message the message to be sent
     */
    public void sendPacket(String message) {
        if (socket == null || socket.isClosed()) {
            LOGGER.warning("Socket is closed, unable to send packet."); // Log a warning if the socket is closed
            return; // Don't attempt to send if the socket is closed
        }
        try {
            byte[] buffer = message.getBytes(); // Convert message to bytes
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT); // Create a DatagramPacket
            socket.send(packet); // Send the packet
            LOGGER.info("Sent packet: " + message); // Log the sent message
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error sending packet: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the multicast socket.
     *
     * @return the multicast socket
     */
    public MulticastSocket getSocket() {
        return socket;
    }
}
