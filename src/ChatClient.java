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

    final String username;
    private final Set<String> users;
    private final MulticastNetworking networking;

    /**
     * Constructor for ChatClient.
     * Sets up the UI and networking, and starts the chat client.
     *
     * @param username the username of the chat client
     * @throws IOException if an I/O error occurs
     */
    public ChatClient(String username) throws IOException {
        this.username = username;
        users = new HashSet<>();

        setupUI(); // Setup the GUI
        networking = new MulticastNetworking(this); // Setup networking

        // Add a shutdown hook to leave the chat gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(this::leaveChat));
        LOGGER.info("Chat client started for user: " + username);
    }

    /**
     * Sets up the user interface for the chat client.
     */
    private void setupUI() {
        SwingUtilities.invokeLater(() -> {
            setTitle("Chat Client - " + username);
            setSize(600, 500);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Setup chat area
            chatArea = new JTextArea();
            chatArea.setEditable(false);
            JScrollPane chatScrollPane = new JScrollPane(chatArea);

            // Setup input field
            inputField = new JTextField();

            // Setup user list
            userModel = new DefaultListModel<>();
            userList = new JList<>(userModel);
            JScrollPane userScrollPane = new JScrollPane(userList);
            userScrollPane.setPreferredSize(new Dimension(200, 0));

            // Setup split pane for chat area and user list
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScrollPane, userScrollPane);
            splitPane.setResizeWeight(0.8);
            splitPane.setDividerLocation(400);
            splitPane.setDividerSize(1); // Set divider size to 1 to make it almost invisible

            // Setup send button
            JButton sendButton = new JButton("Send");
            sendButton.addActionListener(_ -> sendMessage());

            // Create a panel for the input field and send button
            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(inputField, BorderLayout.CENTER);
            inputPanel.add(sendButton, BorderLayout.EAST);

            // Adjust the size of the send button to match the input field
            Dimension buttonSize = new Dimension(180, inputField.getPreferredSize().height);
            sendButton.setPreferredSize(buttonSize);

            // Add components to the frame
            add(splitPane, BorderLayout.CENTER);
            add(inputPanel, BorderLayout.SOUTH);

            // Setup disconnect button
            JButton disconnectButton = new JButton("Disconnect");
            add(disconnectButton, BorderLayout.NORTH);

            // Setup action listeners for input field and disconnect button
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

    /**
     * Displays a message in the chat area.
     *
     * @param message the message to be displayed
     */
    public void displayMessage(String message) {
        SwingUtilities.invokeLater(() -> chatArea.append(message + "\n"));
    }

    /**
     * Updates the user list displayed in the chat client.
     *
     * @param users the set of users to be displayed
     */
    public void updateUserList(Set<String> users) {
        SwingUtilities.invokeLater(() -> {
            userModel.clear();
            for (String user : users) {
                userModel.addElement(user);
            }
        });
    }

    /**
     * Sends a message to the chat.
     */
    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            networking.sendPacket("MESSAGE:" + username + ": " + message); // Send the message
            inputField.setText(""); // Clear the input field
            LOGGER.info("Sent message: " + message);
        }
    }

    /**
     * Leaves the chat and logs out the user.
     */
    private void leaveChat() {
        networking.leaveChat(username);
        LOGGER.info("User " + username + " has left the chat");
    }

    /**
     * Returns the set of users in the chat.
     *
     * @return the set of users
     */
    public Set<String> getUsers() {
        return users;
    }

    /**
     * Shows the username input dialog and starts a new chat client.
     */
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

    /**
     * The main method to start the chat client application.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClient::showUsernameInput);
    }
}
