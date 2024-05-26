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

    public MulticastNetworking(ChatClient client) throws IOException {
        this.client = client;
        setupNetworking();
        messageReceiver = new MessageReceiver(this, client);
        new Thread(messageReceiver).start();
        joinChat(client.username);
        requestUserList(client.username);
    }

    private void setupNetworking() throws IOException {
        socket = new MulticastSocket(PORT);
        group = InetAddress.getByName(MULTICAST_ADDRESS);
        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
        socket.joinGroup(new InetSocketAddress(group, PORT), networkInterface);
    }

    public void joinChat(String username) {
        sendPacket("JOIN:" + username);
        client.getUsers().add(username);
        client.updateUserList(client.getUsers());
    }

    public void leaveChat(String username) {
        messageReceiver.stopRunning();
        sendPacket("LEAVE:" + username);
        client.getUsers().remove(username);
        if (socket != null && !socket.isClosed()) {
            try {
                NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                socket.leaveGroup(new InetSocketAddress(group, PORT), networkInterface);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error leaving group: " + e.getMessage(), e);
            }
            socket.close();
        }
    }

    public void requestUserList(String username) {
        sendPacket("REQUEST_USER_LIST:" + username);
    }

    public void sendUserList(String requester, Set<String> users) {
        for (String user : users) {
            sendPacket("USER_LIST:" + requester + ":" + user);
        }
    }

    public void sendPacket(String message) {
        if (socket == null || socket.isClosed()) {
            System.out.println("Socket is closed, unable to send packet.");
            return; // Don't attempt to send if the socket is closed
        }
        try {
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
            socket.send(packet);
            System.out.println("Sent packet: " + message); // Add logging here
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error sending packet: " + e.getMessage(), e);
        }
    }

    public MulticastSocket getSocket() {
        return socket;
    }
}
