package com.quangtuan.chat.client;

import com.quangtuan.chat.common.ChatPacket;
import com.quangtuan.chat.common.PacketType;

import javax.swing.SwingUtilities;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkClient {
    private Socket socket;
    private ObjectOutputStream out;
    private Consumer<ChatPacket> listener;
    private boolean connected;
    private boolean closing;

    public void connect(String host, int port, Consumer<ChatPacket> listener) throws Exception {
        this.listener = listener;
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        connected = true;
        closing = false;
        Thread reader = new Thread(this::readLoop, "server-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public synchronized void send(ChatPacket packet) {
        try {
            out.writeObject(packet);
            out.flush();
            out.reset();
        } catch (Exception e) {
            notifyPacket(ChatPacket.of(PacketType.ERROR).put("message", e.getMessage()));
        }
    }

    public synchronized void disconnect() {
        closing = true;
        connected = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
        socket = null;
        out = null;
    }

    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    private void readLoop() {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            while (true) {
                Object raw = in.readObject();
                if (raw instanceof ChatPacket packet) {
                    notifyPacket(packet);
                }
            }
        } catch (Exception e) {
            connected = false;
            if (!closing) {
                notifyPacket(ChatPacket.of(PacketType.ERROR).put("message", "Mat ket noi server: " + e.getMessage()));
            }
        }
    }

    private void notifyPacket(ChatPacket packet) {
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.accept(packet));
        }
    }
}
