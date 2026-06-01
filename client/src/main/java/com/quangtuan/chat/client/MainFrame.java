package com.quangtuan.chat.client;

import com.quangtuan.chat.common.ChatMessage;
import com.quangtuan.chat.common.ChatPacket;
import com.quangtuan.chat.common.GroupInfo;
import com.quangtuan.chat.common.PacketType;
import com.quangtuan.chat.common.UserInfo;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainFrame extends JFrame {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5555;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final NetworkClient network = new NetworkClient();
    private final DefaultListModel<UserInfo> onlineModel = new DefaultListModel<>();
    private final DefaultListModel<GroupInfo> groupModel = new DefaultListModel<>();
    private final JList<UserInfo> onlineList = new JList<>(onlineModel);
    private final JList<GroupInfo> groupList = new JList<>(groupModel);
    private final DefaultComboBoxModel<GroupInfo> historyGroupModel = new DefaultComboBoxModel<>();
    private final JComboBox<GroupInfo> historyGroupCombo = new JComboBox<>(historyGroupModel);
    private final JTabbedPane mainTabs = new JTabbedPane(JTabbedPane.LEFT);
    private final JTabbedPane conversationTabs = new JTabbedPane();
    private final Map<String, JTextArea> conversations = new HashMap<>();
    private final Map<String, JPanel> conversationPanels = new HashMap<>();
    private final Map<String, List<ChatMessage>> conversationMessages = new HashMap<>();
    private final Set<Integer> joinedGroupIds = new HashSet<>();
    private final DefaultListModel<ChatMessage> historyModel = new DefaultListModel<>();
    private final JList<ChatMessage> historyList = new JList<>(historyModel);
    private final JLabel sessionStatus = new JLabel("Chua dang nhap");
    private JButton loginButton;
    private JButton registerButton;
    private JButton logoutButton;
    private UserInfo currentUser;
    private File selectedFile;

    public MainFrame() {
        super("JavaChat Swing");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setLocationRelativeTo(null);
        buildUi();
    }

    private void buildUi() {
        logoutButton = new JButton("Dang xuat");
        logoutButton.setVisible(false);
        logoutButton.addActionListener(e -> logout());

        JPanel sessionPanel = new JPanel(new BorderLayout(8, 0));
        sessionPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        sessionPanel.add(sessionStatus, BorderLayout.WEST);
        sessionPanel.add(logoutButton, BorderLayout.EAST);

        mainTabs.addTab("Dang nhap", loginPanel());
        mainTabs.addTab("Chat", chatPanel());
        mainTabs.addTab("Nhom", groupPanel());
        mainTabs.addTab("Lich su", historyPanel());
        mainTabs.setEnabledAt(1, false);
        mainTabs.setEnabledAt(2, false);
        mainTabs.setEnabledAt(3, false);

        JPanel root = new JPanel(new BorderLayout());
        root.add(sessionPanel, BorderLayout.NORTH);
        root.add(mainTabs, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel loginPanel() {
        JTextField username = new JTextField(18);
        JPasswordField password = new JPasswordField(18);
        loginButton = new JButton("Dang nhap");
        registerButton = new JButton("Dang ky");

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        addRow(panel, c, 0, "Username", username);
        addRow(panel, c, 1, "Password", password);
        c.gridx = 1;
        c.gridy = 2;
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(loginButton);
        buttons.add(registerButton);
        panel.add(buttons, c);

        loginButton.addActionListener(e -> connectAndSend(PacketType.LOGIN, username.getText(), new String(password.getPassword())));
        registerButton.addActionListener(e -> connectAndSend(PacketType.REGISTER, username.getText(), new String(password.getPassword())));
        return panel;
    }

    private JPanel chatPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        onlineList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JButton openDirect = new JButton("Mo chat rieng");
        openDirect.addActionListener(e -> {
            UserInfo selected = onlineList.getSelectedValue();
            if (selected != null && (currentUser == null || selected.id() != currentUser.id())) {
                openConversation("u:" + selected.id(), selected.username(), false, selected, null);
            }
        });
        left.add(new JLabel("User online"), BorderLayout.NORTH);
        left.add(new JScrollPane(onlineList), BorderLayout.CENTER);
        left.add(openDirect, BorderLayout.SOUTH);
        root.add(left, BorderLayout.WEST);
        root.add(conversationTabs, BorderLayout.CENTER);
        return root;
    }

    private JPanel groupPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupList.setCellRenderer((list, value, index, selected, focused) -> {
            String suffix = joinedGroupIds.contains(value.id()) ? " (da tham gia)" : " (chua tham gia)";
            JLabel label = new JLabel(value.name() + suffix);
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton create = new JButton("Tao group");
        JButton join = new JButton("Tham gia");
        JButton leave = new JButton("Roi group");
        JButton open = new JButton("Mo chat group");
        buttons.add(create);
        buttons.add(join);
        buttons.add(leave);
        buttons.add(open);
        root.add(new JScrollPane(groupList), BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        create.addActionListener(e -> createGroup());
        join.addActionListener(e -> {
            GroupInfo group = groupList.getSelectedValue();
            if (group != null) {
                network.send(ChatPacket.of(PacketType.JOIN_GROUP).put("group", group));
            }
        });
        leave.addActionListener(e -> {
            GroupInfo group = groupList.getSelectedValue();
            if (group != null) {
                network.send(ChatPacket.of(PacketType.LEAVE_GROUP).put("group", group));
            }
        });
        open.addActionListener(e -> {
            GroupInfo group = groupList.getSelectedValue();
            if (group != null) {
                if (joinedGroupIds.contains(group.id())) {
                    openConversation("g:" + group.id(), group.name(), true, null, group);
                    mainTabs.setSelectedIndex(1);
                } else {
                    JOptionPane.showMessageDialog(this, "Ban can tham gia group truoc khi chat", "Chua tham gia", JOptionPane.WARNING_MESSAGE);
                }
            }
        });
        return root;
    }

    private JPanel historyPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        historyList.setCellRenderer((list, value, index, selected, focused) -> {
            JLabel label = new JLabel(format(value));
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        JPanel groupPicker = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadGroup = new JButton("Tai lich su group");
        groupPicker.add(new JLabel("Nhom da tham gia"));
        groupPicker.add(historyGroupCombo);
        groupPicker.add(loadGroup);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadDirect = new JButton("Tai lich su chat rieng");
        JButton delete = new JButton("Xoa dong da chon");
        JButton saveFile = new JButton("Luu file da chon");
        buttons.add(loadDirect);
        buttons.add(delete);
        buttons.add(saveFile);
        root.add(new JScrollPane(historyList), BorderLayout.CENTER);
        root.add(groupPicker, BorderLayout.NORTH);
        root.add(buttons, BorderLayout.SOUTH);

        loadDirect.addActionListener(e -> {
            UserInfo selected = onlineList.getSelectedValue();
            if (selected != null) {
                network.send(ChatPacket.of(PacketType.HISTORY).put("scope", "direct").put("user", selected));
            } else {
                String username = JOptionPane.showInputDialog(this, "Username can xem lich su");
                if (username != null && !username.isBlank()) {
                    network.send(ChatPacket.of(PacketType.HISTORY).put("scope", "direct").put("username", username));
                }
            }
        });
        loadGroup.addActionListener(e -> {
            GroupInfo selected = (GroupInfo) historyGroupCombo.getSelectedItem();
            if (selected != null) {
                network.send(ChatPacket.of(PacketType.HISTORY).put("scope", "group").put("group", selected));
            } else {
                JOptionPane.showMessageDialog(this, "Ban chua tham gia group nao", "Chua co group", JOptionPane.WARNING_MESSAGE);
            }
        });
        delete.addActionListener(e -> {
            ChatMessage msg = historyList.getSelectedValue();
            if (msg != null) {
                network.send(ChatPacket.of(PacketType.DELETE_HISTORY).put("messageId", msg.id()));
                historyModel.removeElement(msg);
            }
        });
        saveFile.addActionListener(e -> saveSelectedHistoryFile());
        return root;
    }

    private void connectAndSend(PacketType type, String username, String password) {
        try {
            if (!network.isConnected()) {
                network.connect(DEFAULT_HOST, DEFAULT_PORT, this::onPacket);
            }
            network.send(ChatPacket.of(type).put("username", username).put("password", password));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Loi ket noi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void logout() {
        if (network.isConnected()) {
            network.send(ChatPacket.of(PacketType.LOGOUT));
        }
        network.disconnect();
        currentUser = null;
        selectedFile = null;
        onlineModel.clear();
        groupModel.clear();
        historyGroupModel.removeAllElements();
        joinedGroupIds.clear();
        historyModel.clear();
        conversations.clear();
        conversationPanels.clear();
        conversationMessages.clear();
        conversationTabs.removeAll();
        mainTabs.setEnabledAt(0, true);
        mainTabs.setEnabledAt(1, false);
        mainTabs.setEnabledAt(2, false);
        mainTabs.setEnabledAt(3, false);
        mainTabs.setSelectedIndex(0);
        loginButton.setVisible(true);
        registerButton.setVisible(true);
        logoutButton.setVisible(false);
        sessionStatus.setText("Chua dang nhap");
        setTitle("JavaChat Swing");
    }

    private void onPacket(ChatPacket packet) {
        switch (packet.type()) {
            case OK -> {
                JOptionPane.showMessageDialog(this, packet.text("message"));
                UserInfo user = packet.get("user");
                if (user != null) {
                    currentUser = user;
                    mainTabs.setEnabledAt(0, false);
                    mainTabs.setEnabledAt(1, true);
                    mainTabs.setEnabledAt(2, true);
                    mainTabs.setEnabledAt(3, true);
                    mainTabs.setSelectedIndex(1);
                    loginButton.setVisible(false);
                    registerButton.setVisible(false);
                    logoutButton.setVisible(true);
                    sessionStatus.setText("Dang nhap: " + currentUser.username() + " (" + DEFAULT_HOST + ":" + DEFAULT_PORT + ")");
                    setTitle("JavaChat Swing - " + currentUser.username());
                }
            }
            case ERROR -> JOptionPane.showMessageDialog(this, packet.text("message"), "Loi", JOptionPane.ERROR_MESSAGE);
            case ONLINE_USERS -> updateUsers(packet.get("users"));
            case GROUPS -> updateGroups(packet.get("groups"), packet.get("joinedGroups"));
            case MESSAGE -> receiveMessage(packet.get("message"));
            case HISTORY -> showHistory(packet.get("messages"));
            default -> {
            }
        }
    }

    private void updateUsers(List<UserInfo> users) {
        onlineModel.clear();
        if (users != null) {
            users.forEach(onlineModel::addElement);
        }
    }

    private void updateGroups(List<GroupInfo> groups, List<GroupInfo> joinedGroups) {
        joinedGroupIds.clear();
        Integer selectedHistoryGroupId = null;
        GroupInfo selectedHistoryGroup = (GroupInfo) historyGroupCombo.getSelectedItem();
        if (selectedHistoryGroup != null) {
            selectedHistoryGroupId = selectedHistoryGroup.id();
        }
        historyGroupModel.removeAllElements();
        if (joinedGroups != null) {
            for (GroupInfo group : joinedGroups) {
                joinedGroupIds.add(group.id());
                historyGroupModel.addElement(group);
                if (selectedHistoryGroupId != null && selectedHistoryGroupId == group.id()) {
                    historyGroupCombo.setSelectedItem(group);
                }
            }
        }
        groupModel.clear();
        if (groups != null) {
            groups.forEach(groupModel::addElement);
        }
        groupList.repaint();
    }

    private void openConversation(String key, String title, boolean group, UserInfo user, GroupInfo groupInfo) {
        if (conversations.containsKey(key)) {
            conversationTabs.setSelectedComponent(conversationPanels.get(key));
            return;
        }
        JTextArea area = new JTextArea();
        area.setEditable(false);
        JTextField input = new JTextField();
        JButton attach = new JButton("Chon file");
        JButton saveFile = new JButton("Luu file trong chat");
        JButton send = new JButton("Gui");
        JLabel fileLabel = new JLabel("Chua chon file");
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filePanel.add(attach);
        filePanel.add(saveFile);
        filePanel.add(fileLabel);
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(filePanel, BorderLayout.NORTH);
        bottom.add(send, BorderLayout.EAST);
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        conversations.put(key, area);
        conversationPanels.put(key, panel);
        conversationMessages.put(key, new ArrayList<>());
        conversationTabs.addTab(title, panel);
        conversationTabs.setSelectedComponent(panel);

        attach.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedFile = chooser.getSelectedFile();
                fileLabel.setText(selectedFile.getName());
            }
        });
        saveFile.addActionListener(e -> saveConversationFile(key));
        send.addActionListener(e -> sendMessage(group, user, groupInfo, input, fileLabel));
    }

    private void sendMessage(boolean group, UserInfo receiver, GroupInfo groupInfo, JTextField input, JLabel fileLabel) {
        try {
            String text = input.getText().trim();
            byte[] fileData = null;
            String fileName = null;
            if (selectedFile != null) {
                fileData = Files.readAllBytes(selectedFile.toPath());
                fileName = selectedFile.getName();
            }
            if (text.isBlank() && fileData == null) {
                return;
            }
            ChatPacket packet = ChatPacket.of(group ? PacketType.SEND_GROUP : PacketType.SEND_DIRECT)
                    .put("content", text)
                    .put("fileName", fileName)
                    .put("fileData", fileData);
            if (group) {
                packet.put("group", groupInfo);
            } else {
                packet.put("receiver", receiver);
            }
            network.send(packet);
            input.setText("");
            selectedFile = null;
            fileLabel.setText("Chua chon file");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Khong gui duoc", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void receiveMessage(ChatMessage message) {
        String key;
        String title;
        if (message.groupId() != null) {
            key = "g:" + message.groupId();
            title = message.groupName();
            openConversation(key, title, true, null, new GroupInfo(message.groupId(), message.groupName()));
        } else {
            int otherId = message.senderId() == currentUser.id() ? message.receiverUserId() : message.senderId();
            key = "u:" + otherId;
            title = message.senderId() == currentUser.id() ? "Ban -> user #" + otherId : message.senderName();
            openConversation(key, title, false, new UserInfo(otherId, title), null);
        }
        conversationMessages.computeIfAbsent(key, ignored -> new ArrayList<>()).add(message);
        conversations.get(key).append(format(message) + System.lineSeparator());
    }

    private void showHistory(List<ChatMessage> messages) {
        historyModel.clear();
        if (messages != null) {
            messages.forEach(historyModel::addElement);
        }
        mainTabs.setSelectedIndex(3);
    }

    private void createGroup() {
        String name = JOptionPane.showInputDialog(this, "Ten group");
        if (name != null && !name.isBlank()) {
            network.send(ChatPacket.of(PacketType.CREATE_GROUP).put("name", name));
        }
    }

    private void saveConversationFile(String key) {
        List<ChatMessage> messages = conversationMessages.get(key);
        if (messages == null) {
            return;
        }
        List<FileChoice> files = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.hasFile()) {
                files.add(new FileChoice(message));
            }
        }
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chat nay chua co file");
            return;
        }
        FileChoice choice = files.size() == 1
                ? files.get(0)
                : (FileChoice) JOptionPane.showInputDialog(
                this,
                "Chon file can luu",
                "Luu file trong chat",
                JOptionPane.PLAIN_MESSAGE,
                null,
                files.toArray(),
                files.get(0));
        if (choice != null) {
            saveFile(choice.message());
        }
    }

    private void saveSelectedHistoryFile() {
        ChatMessage msg = historyList.getSelectedValue();
        if (msg != null && msg.hasFile()) {
            saveFile(msg);
        } else {
            JOptionPane.showMessageDialog(this, "Hay chon mot dong lich su co file");
        }
    }

    private void saveFile(ChatMessage msg) {
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File(msg.fileName()));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                Path target = chooser.getSelectedFile().toPath();
                Files.write(target, msg.fileData());
                JOptionPane.showMessageDialog(this, "Da luu file: " + target);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Khong luu duoc file", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String format(ChatMessage msg) {
        StringBuilder line = new StringBuilder();
        if (msg.createdAt() != null) {
            line.append("[").append(TIME.format(msg.createdAt())).append("] ");
        }
        line.append(msg.senderName()).append(": ");
        if (msg.content() != null && !msg.content().isBlank()) {
            line.append(msg.content());
        }
        if (msg.hasFile()) {
            line.append(" [file: ").append(msg.fileName()).append(", ").append(msg.fileData().length).append(" bytes]");
        }
        return line.toString();
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String label, java.awt.Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    private record FileChoice(ChatMessage message) {
        @Override
        public String toString() {
            String time = message.createdAt() == null ? "" : TIME.format(message.createdAt()) + " - ";
            return time + message.senderName() + " - " + message.fileName() + " (" + message.fileData().length + " bytes)";
        }
    }
}
