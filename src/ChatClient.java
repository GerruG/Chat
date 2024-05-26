import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatClient extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(ChatClient.class.getName());

    private JTextArea chatArea;
    private JTextField inputField;
    private JList<String> userList;
    private DefaultListModel<String> userModel;

    private final String username;
    private final Set<String> users;
    private final MulticastNetworking networking;

    public ChatClient(String username) throws IOException {
        this.username = username;
        users = new HashSet<>();

        setupUI(); // Setup GUI
        networking = new MulticastNetworking(this); // Setup networking

        Runtime.getRuntime().addShutdownHook(new Thread(this::leaveChat));
        LOGGER.info("Chat client started for user: " + username);
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
            splitPane.setDividerSize(1); // Set divider size to 1 to make it almost invisible

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
                dispose(); // Close the current chat window
                showUsernameInput(); // Show the username input dialog again
            });

            setVisible(true);
        });
        LOGGER.info("UI setup complete for user: " + username);
    }

    public void displayMessage(String message) {
        SwingUtilities.invokeLater(() -> chatArea.append(message + "\n"));
    }

    public void updateUserList(Set<String> users) {
        SwingUtilities.invokeLater(() -> {
            userModel.clear();
            for (String user : users) {
                userModel.addElement(user);
            }
        });
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            networking.sendPacket("MESSAGE:" + username + ": " + message);
            inputField.setText("");
            LOGGER.info("Sent message: " + message);
        }
    }

    private void leaveChat() {
        networking.leaveChat(username);
        LOGGER.info("User " + username + " has left the chat");
    }

    public Set<String> getUsers() {
        return users;
    }

    public static void showUsernameInput() {
        SwingUtilities.invokeLater(() -> {
            String username = JOptionPane.showInputDialog(null, "Enter your username:", "Chat Client", JOptionPane.PLAIN_MESSAGE);
            if (username != null && !username.trim().isEmpty()) {
                try {
                    new ChatClient(username); // Start a new ChatClient instance
                } catch (IOException e) {
                    Logger.getLogger(ChatClient.class.getName()).log(Level.SEVERE, "Error starting chat client: " + e.getMessage(), e);
                }
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClient::showUsernameInput);
    }
}
