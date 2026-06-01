package com.quangtuan.chat.server;

import com.quangtuan.chat.common.UserInfo;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ServerFrame extends JFrame {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ChatServerApp server;
    private final DefaultListModel<UserInfo> clientModel = new DefaultListModel<>();
    private final JLabel statusLabel = new JLabel("Trang thai: Da dong");
    private final JLabel clientCountLabel = new JLabel("Client dang ket noi: 0");
    private final JTextArea logArea = new JTextArea(8, 80);
    private final JButton startButton = new JButton("Mo server");
    private final JButton stopButton = new JButton("Dong server");
    private boolean busy;

    ServerFrame(ChatServerApp server) {
        super("JavaChat Server");
        this.server = server;
        this.server.setMonitor(new DashboardMonitor());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(840, 560));
        setLocationRelativeTo(null);
        buildUi();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                server.stopServer();
            }
        });
        startServerAsync();
    }

    private void buildUi() {
        JPanel statusPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        statusPanel.add(statusLabel);
        statusPanel.add(clientCountLabel);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(startButton);
        actions.add(stopButton);

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        top.add(statusPanel, BorderLayout.CENTER);
        top.add(actions, BorderLayout.EAST);

        JList<UserInfo> clientList = new JList<>(clientModel);
        clientList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel clients = new JPanel(new BorderLayout(0, 6));
        clients.setBorder(BorderFactory.createTitledBorder("Danh sach client dang ket noi"));
        clients.add(new JScrollPane(clientList), BorderLayout.CENTER);

        JTextArea configArea = new JTextArea(configText());
        configArea.setEditable(false);
        configArea.setFont(Font.decode(Font.MONOSPACED));
        JPanel config = new JPanel(new BorderLayout());
        config.setBorder(BorderFactory.createTitledBorder("Config server"));
        config.add(new JScrollPane(configArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, config, clients);
        split.setResizeWeight(0.45);

        logArea.setEditable(false);
        logArea.setFont(Font.decode(Font.MONOSPACED));
        JPanel logs = new JPanel(new BorderLayout());
        logs.setBorder(BorderFactory.createTitledBorder("Log"));
        logs.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.add(top, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.add(logs, BorderLayout.SOUTH);
        setContentPane(root);

        startButton.addActionListener(e -> startServerAsync());
        stopButton.addActionListener(e -> {
            server.stopServer();
            updateControls();
        });
        updateControls();
    }

    private String configText() {
        return String.join(System.lineSeparator(),
                "CHAT_PORT=" + ServerConfig.CHAT_PORT,
                "DB_HOST=" + ServerConfig.DB_HOST,
                "DB_PORT=" + ServerConfig.DB_PORT,
                "DB_NAME=" + ServerConfig.DB_NAME,
                "DB_USER=" + ServerConfig.DB_USER,
                "DB_PASSWORD=" + ServerConfig.DB_PASSWORD,
                "JDBC_URL=" + ServerConfig.jdbcUrl());
    }

    private void startServerAsync() {
        busy = true;
        updateControls();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                server.startServer();
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    String message = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
                    appendLog("Khong mo duoc server: " + message);
                    JOptionPane.showMessageDialog(ServerFrame.this, message, "Loi mo server", JOptionPane.ERROR_MESSAGE);
                }
                updateControls();
            }
        }.execute();
    }

    private void updateClients(List<UserInfo> users) {
        clientModel.clear();
        if (users != null) {
            users.forEach(clientModel::addElement);
        }
        clientCountLabel.setText("Client dang ket noi: " + clientModel.size());
    }

    private void updateControls() {
        boolean running = server.isRunning();
        startButton.setEnabled(!busy && !running);
        stopButton.setEnabled(!busy && running);
        if (!running) {
            statusLabel.setText("Trang thai: Da dong");
        }
    }

    private void appendLog(String message) {
        logArea.append("[" + CLOCK.format(LocalTime.now()) + "] " + message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private class DashboardMonitor implements ChatServerApp.ServerMonitor {
        @Override
        public void statusChanged(boolean running, String status) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Trang thai: " + status);
                updateControls();
            });
        }

        @Override
        public void clientsChanged(List<UserInfo> users) {
            SwingUtilities.invokeLater(() -> updateClients(users));
        }

        @Override
        public void log(String message) {
            SwingUtilities.invokeLater(() -> appendLog(message));
        }
    }
}
