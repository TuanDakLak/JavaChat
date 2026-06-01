package com.quangtuan.chat.server;

import com.quangtuan.chat.common.ChatPacket;
import com.quangtuan.chat.common.PacketType;
import com.quangtuan.chat.common.UserInfo;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServerApp {
    private final Database database = new Database();
    private final Map<Integer, ClientHandler> online = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private volatile ServerSocket serverSocket;
    private volatile boolean running;
    private volatile ServerMonitor monitor = ServerMonitor.NONE;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(() -> new ServerFrame(new ChatServerApp()).setVisible(true));
    }

    public void setMonitor(ServerMonitor monitor) {
        this.monitor = monitor == null ? ServerMonitor.NONE : monitor;
    }

    public void startServer() throws SQLException, IOException {
        synchronized (lifecycleLock) {
            if (running) {
                return;
            }
        }
        database.init();

        ServerSocket openedSocket = new ServerSocket(ServerConfig.CHAT_PORT);
        synchronized (lifecycleLock) {
            if (running) {
                openedSocket.close();
                return;
            }
            serverSocket = openedSocket;
            running = true;
        }
        notifyStatus("Dang chay tren cong " + ServerConfig.CHAT_PORT);
        notifyLog("JavaChat server listening on port " + ServerConfig.CHAT_PORT);

        Thread acceptThread = new Thread(() -> acceptLoop(openedSocket), "server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stopServer() {
        List<ClientHandler> handlers;
        synchronized (lifecycleLock) {
            if (!running && serverSocket == null) {
                return;
            }
            running = false;
            closeServerSocket();
            handlers = new ArrayList<>(online.values());
            online.clear();
        }

        for (ClientHandler handler : handlers) {
            handler.closeQuietly();
        }
        notifyClientsChanged();
        notifyStatus("Da dong");
        notifyLog("Server da dong");
    }

    public boolean isRunning() {
        return running;
    }

    private void acceptLoop(ServerSocket listeningSocket) {
        while (running) {
            try {
                Socket socket = listeningSocket.accept();
                notifyLog("Client ket noi: " + socket.getRemoteSocketAddress());
                new Thread(new ClientHandler(socket, this, database), "client-" + socket.getPort()).start();
            } catch (SocketException e) {
                if (running) {
                    notifyLog("Loi socket server: " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                if (running) {
                    notifyLog("Khong nhan duoc client: " + e.getMessage());
                }
            }
        }
        if (running) {
            stopServer();
        }
    }

    private void closeServerSocket() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        } finally {
            serverSocket = null;
        }
    }

    void markOnline(UserInfo user, ClientHandler handler) {
        ClientHandler previous = online.put(user.id(), handler);
        if (previous != null && previous != handler) {
            previous.closeQuietly();
        }
        notifyLog(user.username() + " da dang nhap");
        notifyClientsChanged();
        broadcastOnlineUsers();
    }

    void markOffline(int userId, ClientHandler handler) {
        boolean removed = online.remove(userId, handler);
        if (removed) {
            notifyClientsChanged();
            broadcastOnlineUsers();
            notifyLog("User #" + userId + " da ngat ket noi");
        }
    }

    boolean isOnline(int userId) {
        return online.containsKey(userId);
    }

    void sendToUser(int userId, ChatPacket packet) {
        ClientHandler handler = online.get(userId);
        if (handler != null) {
            handler.send(packet);
        }
    }

    void broadcastOnlineUsers() {
        ChatPacket packet = ChatPacket.of(PacketType.ONLINE_USERS).put("users", onlineUsers());
        for (ClientHandler handler : online.values()) {
            handler.send(packet);
        }
    }

    void broadcastGroups() {
        for (ClientHandler handler : online.values()) {
            try {
                handler.sendGroups();
            } catch (SQLException e) {
                handler.send(ChatPacket.of(PacketType.ERROR).put("message", e.getMessage()));
            }
        }
    }

    List<UserInfo> onlineUsers() {
        List<UserInfo> users = new ArrayList<>();
        for (ClientHandler handler : online.values()) {
            users.add(handler.user());
        }
        users.sort(Comparator.comparing(UserInfo::username));
        return users;
    }

    private void notifyStatus(String status) {
        monitor.statusChanged(running, status);
    }

    private void notifyClientsChanged() {
        monitor.clientsChanged(onlineUsers());
    }

    private void notifyLog(String message) {
        monitor.log(message);
    }

    public interface ServerMonitor {
        ServerMonitor NONE = new ServerMonitor() {
            @Override
            public void statusChanged(boolean running, String status) {
            }

            @Override
            public void clientsChanged(List<UserInfo> users) {
            }

            @Override
            public void log(String message) {
            }
        };

        void statusChanged(boolean running, String status);

        void clientsChanged(List<UserInfo> users);

        void log(String message);
    }
}
