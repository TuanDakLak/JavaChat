package com.quangtuan.chat.server;

import com.quangtuan.chat.common.ChatMessage;
import com.quangtuan.chat.common.ChatPacket;
import com.quangtuan.chat.common.GroupInfo;
import com.quangtuan.chat.common.PacketType;
import com.quangtuan.chat.common.UserInfo;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.SQLException;

class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServerApp server;
    private final Database database;
    private ObjectOutputStream out;
    private UserInfo user;

    ClientHandler(Socket socket, ChatServerApp server, Database database) {
        this.socket = socket;
        this.server = server;
        this.database = database;
    }

    @Override
    public void run() {
        try (Socket ignored = socket;
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
            this.out = output;
            while (true) {
                Object raw = input.readObject();
                if (raw instanceof ChatPacket packet) {
                    try {
                        handle(packet);
                    } catch (Exception e) {
                        send(ChatPacket.of(PacketType.ERROR).put("message", e.getMessage()));
                    }
                }
            }
        } catch (EOFException ignored) {
            // client disconnected
        } catch (Exception e) {
            send(ChatPacket.of(PacketType.ERROR).put("message", e.getMessage()));
        } finally {
            if (user != null) {
                server.markOffline(user.id(), this);
            }
        }
    }

    UserInfo user() {
        return user;
    }

    synchronized void send(ChatPacket packet) {
        if (out == null) {
            return;
        }
        try {
            out.writeObject(packet);
            out.flush();
            out.reset();
        } catch (Exception ignored) {
            closeQuietly();
        }
    }

    void closeQuietly() {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }

    private void handle(ChatPacket packet) throws SQLException {
        switch (packet.type()) {
            case REGISTER -> register(packet);
            case LOGIN -> login(packet);
            case LOGOUT -> closeQuietly();
            case ONLINE_USERS -> send(ChatPacket.of(PacketType.ONLINE_USERS).put("users", server.onlineUsers()));
            case CREATE_GROUP -> createGroup(packet);
            case JOIN_GROUP -> joinGroup(packet);
            case LEAVE_GROUP -> leaveGroup(packet);
            case GROUPS -> sendGroups();
            case SEND_DIRECT -> sendDirect(packet);
            case SEND_GROUP -> sendGroup(packet);
            case HISTORY -> history(packet);
            case DELETE_HISTORY -> deleteHistory(packet);
            default -> send(ChatPacket.of(PacketType.ERROR).put("message", "Unsupported packet: " + packet.type()));
        }
    }

    private void register(ChatPacket packet) {
        try {
            String username = packet.text("username").trim();
            String password = packet.text("password");
            require(username.length() >= 3, "Username phải có ít nhất 3 ký tự");
            require(password.length() >= 3, "Password phải có ít nhất 3 ký tự");
            database.register(username, password);
            send(ChatPacket.of(PacketType.OK).put("message", "Đăng ký thành công, hãy đăng nhập"));
        } catch (Exception e) {
            send(ChatPacket.of(PacketType.ERROR).put("message", "Đăng ký thất bại: " + e.getMessage()));
        }
    }

    private void login(ChatPacket packet) throws SQLException {
        UserInfo loggedIn = database.login(packet.text("username").trim(), packet.text("password"));
        if (loggedIn == null) {
            send(ChatPacket.of(PacketType.ERROR).put("message", "Sai username hoặc password"));
            return;
        }
        user = loggedIn;
        send(ChatPacket.of(PacketType.OK).put("message", "Đăng nhập thành công").put("user", user));
        sendGroups();
        server.markOnline(user, this);
    }

    private void createGroup(ChatPacket packet) throws SQLException {
        requireLoggedIn();
        String name = packet.text("name").trim();
        require(!name.isBlank(), "Tên group không được rỗng");
        GroupInfo group = database.createGroup(name, user.id());
        server.broadcastGroups();
        send(ChatPacket.of(PacketType.OK).put("message", "Đã tạo group " + group.name()).put("group", group));
    }

    void sendGroups() throws SQLException {
        requireLoggedIn();
        send(ChatPacket.of(PacketType.GROUPS)
                .put("groups", database.allGroups())
                .put("joinedGroups", database.groupsForUser(user.id())));
    }

    private void joinGroup(ChatPacket packet) throws SQLException {
        requireLoggedIn();
        GroupInfo group = packet.get("group");
        require(group != null, "Chưa chọn group");
        database.joinGroup(group.id(), user.id());
        send(ChatPacket.of(PacketType.OK).put("message", "Đã tham gia group " + group.name()));
        server.broadcastGroups();
    }

    private void leaveGroup(ChatPacket packet) throws SQLException {
        requireLoggedIn();
        GroupInfo group = packet.get("group");
        require(group != null, "Chưa chọn group");
        database.leaveGroup(group.id(), user.id());
        send(ChatPacket.of(PacketType.OK).put("message", "Đã rời group " + group.name()));
        server.broadcastGroups();
    }

    private void sendDirect(ChatPacket packet) throws SQLException {
        requireLoggedIn();
        UserInfo receiver = packet.get("receiver");
        require(receiver != null, "Chưa chọn người nhận");
        ChatMessage message = database.saveDirect(
                user.id(),
                receiver.id(),
                packet.text("content"),
                packet.get("fileName"),
                packet.get("fileData")
        );
        ChatPacket event = ChatPacket.of(PacketType.MESSAGE).put("message", message).put("scope", "direct");
        server.sendToUser(receiver.id(), event);
        send(event);
    }

    private void sendGroup(ChatPacket packet) throws SQLException {
        requireLoggedIn();
        GroupInfo group = packet.get("group");
        require(group != null, "Chưa chọn group");
        require(database.isGroupMember(group.id(), user.id()), "Bạn cần tham gia group trước khi chat");
        ChatMessage message = database.saveGroup(
                user.id(),
                group.id(),
                packet.text("content"),
                packet.get("fileName"),
                packet.get("fileData")
        );
        ChatPacket event = ChatPacket.of(PacketType.MESSAGE).put("message", message).put("scope", "group");
        for (Integer memberId : database.groupMemberIds(group.id())) {
            server.sendToUser(memberId, event);
        }
    }

    private void history(ChatPacket packet) throws SQLException {
        requireLoggedIn();
        String scope = packet.text("scope");
        if ("group".equals(scope)) {
            GroupInfo group = packet.get("group");
            require(group != null, "Chưa chọn group");
            require(database.isGroupMember(group.id(), user.id()), "Bạn cần tham gia group trước khi xem lịch sử");
            send(ChatPacket.of(PacketType.HISTORY)
                    .put("scope", "group")
                    .put("group", group)
                    .put("messages", database.groupHistory(user.id(), group.id())));
        } else {
            UserInfo other = packet.get("user");
            if (other == null) {
                other = database.findUser(packet.text("username").trim());
            }
            require(other != null, "Không tìm thấy user");
            send(ChatPacket.of(PacketType.HISTORY)
                    .put("scope", "direct")
                    .put("user", other)
                    .put("messages", database.directHistory(user.id(), other.id())));
        }
    }

    private void deleteHistory(ChatPacket packet) throws SQLException {
        requireLoggedIn();
        Integer messageId = packet.get("messageId");
        if (messageId != null) {
            database.deleteForUser(messageId, user.id());
            send(ChatPacket.of(PacketType.OK).put("message", "Đã xoá dòng lịch sử"));
        }
    }

    private void requireLoggedIn() {
        require(user != null, "Bạn cần đăng nhập trước");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
