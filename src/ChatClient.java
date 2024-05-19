import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.*;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatClient extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(ChatClient.class.getName());
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int PORT = 4446;

    private JTextArea chatArea;
    private JTextField inputField;
    private JList<String> userList;
    private DefaultListModel<String> userModel;

    private MulticastSocket socket;
    private InetAddress group;
    private final String username;
    private final Set<String> users;
    private volatile boolean running = true;

    public ChatClient(String username) throws IOException {
        this.username = username;
        users = new HashSet<>();

        setupUI(); // Setup GUI
        setupNetworking(); // Setup networking with UDP and Multicast

        Runtime.getRuntime().addShutdownHook(new Thread(this::leaveChat));
    }

    private void setupUI() {
        SwingUtilities.invokeLater(() -> {
            setTitle("Chat Client - " + username);
            setSize(600, 500);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            chatArea = new JTextArea();
            chatArea.setEditable(false);
            JScrollPane chatScrollPane = new JScrollPane(chatArea);

            inputField = new JTextField();

            userModel = new DefaultListModel<>();
            userList = new JList<>(userModel);
            JScrollPane userScrollPane = new JScrollPane(userList);
            userScrollPane.setPreferredSize(new Dimension(200, 0));

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScrollPane, userScrollPane);
            splitPane.setResizeWeight(0.8);
            splitPane.setDividerLocation(400);

            JButton sendButton = new JButton("Send");
            sendButton.addActionListener(_ -> sendMessage());

            // Create a panel for the input field and send button
            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(inputField, BorderLayout.CENTER);
            inputPanel.add(sendButton, BorderLayout.EAST);

            // Adjust the size of the send button to match the input field
            Dimension buttonSize = new Dimension(180, inputField.getPreferredSize().height);
            sendButton.setPreferredSize(buttonSize);

            add(splitPane, BorderLayout.CENTER);
            add(inputPanel, BorderLayout.SOUTH);

            JButton disconnectButton = new JButton("Disconnect");
            add(disconnectButton, BorderLayout.NORTH);

            inputField.addActionListener(_ -> sendMessage());
            disconnectButton.addActionListener(_ -> {
                leaveChat();
                System.exit(0);
            });

            setVisible(true);
        });
    }

    private void setupNetworking() throws IOException {
        socket = new MulticastSocket(PORT);
        group = InetAddress.getByName(MULTICAST_ADDRESS);
        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
        socket.joinGroup(new InetSocketAddress(group, PORT), networkInterface);

        new Thread(new MessageReceiver()).start();
        joinChat();
        requestUserList();
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            sendPacket("MESSAGE:" + username + ": " + message);
            inputField.setText("");
        }
    }

    private void joinChat() {
        System.out.println("Joining chat as " + username); // Add logging here
        sendPacket("JOIN:" + username);
        users.add(username);
        updateUsersList();
    }

    private void leaveChat() {
        running = false;
        sendPacket("LEAVE:" + username);
        users.remove(username);
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

    private void requestUserList() {
        sendPacket("REQUEST_USER_LIST:" + username);
    }

    private void sendUserList(String requester) {
        for (String user : users) {
            sendPacket("USER_LIST:" + requester + ":" + user);
        }
    }

    private void sendPacket(String message) {
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

    private synchronized void updateUsersList() {
        SwingUtilities.invokeLater(() -> {
            userModel.clear();
            for (String user : users) {
                userModel.addElement(user);
            }
        });
    }

    private class MessageReceiver implements Runnable {
        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
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
                                SwingUtilities.invokeLater(() -> chatArea.append(user + ": " + msg + "\n"));
                            } else {
                                System.out.println("Received malformed message: " + message);
                            }
                        }
                        case "JOIN" -> {
                            String newUser = parts[1];
                            users.add(newUser);
                            updateUsersList();
                            SwingUtilities.invokeLater(() -> chatArea.append(newUser + " has joined the chat.\n"));
                        }
                        case "LEAVE" -> {
                            String leavingUser = parts[1];
                            users.remove(leavingUser);
                            updateUsersList();
                            SwingUtilities.invokeLater(() -> chatArea.append(leavingUser + " has left the chat.\n"));
                        }
                        case "REQUEST_USER_LIST" -> {
                            String requester = parts[1];
                            sendUserList(requester);
                        }
                        case "USER_LIST" -> {
                            if (parts.length == 3) {
                                String user = parts[2];
                                if (!users.contains(user)) {
                                    users.add(user);
                                    updateUsersList();
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
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String username = JOptionPane.showInputDialog(null, "Enter your username:", "Chat Client", JOptionPane.PLAIN_MESSAGE);
            if (username != null && !username.trim().isEmpty()) {
                try {
                    new ChatClient(username);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error starting chat client: " + e.getMessage(), e);
                }
            } else {
                System.exit(0);
            }
        });
    }
}
