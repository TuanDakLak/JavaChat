package com.quangtuan.chat.client;

import com.quangtuan.chat.common.ChatPacket;
import com.quangtuan.chat.common.PacketType;

import javax.swing.SwingUtilities;
import java.net.InetSocketAddress;
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
    private int connectionId;

    public synchronized void connect(String host, int port, Consumer<ChatPacket> listener) throws Exception {
        disconnect();
        this.listener = listener;
        Socket newSocket = new Socket();
        try {
            newSocket.connect(new InetSocketAddress(host, port), 3000);
            ObjectOutputStream output = new ObjectOutputStream(newSocket.getOutputStream());
            socket = newSocket;
            out = output;
            connected = true;
            closing = false;
            connectionId++;
            int readerConnectionId = connectionId;
            Thread reader = new Thread(() -> readLoop(newSocket, readerConnectionId), "server-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (Exception e) {
            try {
                newSocket.close();
            } catch (Exception ignored) {
            }
            connected = false;
            socket = null;
            out = null;
            throw e;
        }
    }

    public synchronized void send(ChatPacket packet) {
        if (!isConnected() || out == null) {
            notifyPacket(ChatPacket.of(PacketType.ERROR).put("message", "Chua ket noi server"));
            return;
        }
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
        connectionId++;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
        socket = null;
        out = null;
    }

    public synchronized boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    private void readLoop(Socket readerSocket, int readerConnectionId) {
        try (ObjectInputStream in = new ObjectInputStream(readerSocket.getInputStream())) {
            while (true) {
                Object raw = in.readObject();
                if (raw instanceof ChatPacket packet) {
                    notifyPacket(packet);
                }
            }
        } catch (Exception e) {
            boolean shouldNotify;
            synchronized (this) {
                if (readerConnectionId != connectionId) {
                    return;
                }
                connected = false;
                socket = null;
                out = null;
                shouldNotify = !closing;
            }
            if (shouldNotify) {
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
