package com.quangtuan.chat.server;

import com.quangtuan.chat.common.ChatPacket;
import com.quangtuan.chat.common.UserInfo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServerApp {
    private final Database database = new Database();
    private final Map<Integer, ClientHandler> online = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        new ChatServerApp().start();
    }

    private void start() throws SQLException, IOException {
        database.init();
        try (ServerSocket serverSocket = new ServerSocket(ServerConfig.CHAT_PORT)) {
            System.out.println("JavaChat server listening on port " + ServerConfig.CHAT_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket, this, database), "client-" + socket.getPort()).start();
            }
        }
    }

    void markOnline(UserInfo user, ClientHandler handler) {
        ClientHandler previous = online.put(user.id(), handler);
        if (previous != null && previous != handler) {
            previous.closeQuietly();
        }
        broadcastOnlineUsers();
    }

    void markOffline(int userId) {
        online.remove(userId);
        broadcastOnlineUsers();
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
        List<UserInfo> users = new ArrayList<>();
        for (ClientHandler handler : online.values()) {
            users.add(handler.user());
        }
        ChatPacket packet = ChatPacket.of(com.quangtuan.chat.common.PacketType.ONLINE_USERS).put("users", users);
        for (ClientHandler handler : online.values()) {
            handler.send(packet);
        }
    }

    void broadcastGroups() {
        for (ClientHandler handler : online.values()) {
            try {
                handler.sendGroups();
            } catch (SQLException e) {
                handler.send(ChatPacket.of(com.quangtuan.chat.common.PacketType.ERROR).put("message", e.getMessage()));
            }
        }
    }

    List<UserInfo> onlineUsers() {
        List<UserInfo> users = new ArrayList<>();
        for (ClientHandler handler : online.values()) {
            users.add(handler.user());
        }
        return users;
    }
}
