package com.quangtuan.chat.client;

import com.quangtuan.chat.common.ChatPacket;
import com.quangtuan.chat.common.GroupInfo;
import com.quangtuan.chat.common.PacketType;
import com.quangtuan.chat.common.UserInfo;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class VoiceChatManager {
    private static final AudioFormat FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);
    private static final int FRAME_BYTES = 3200;
    private static final int STATUS_EVERY_FRAMES = 10;

    private final NetworkClient network;
    private final Listener listener;
    private final Map<Integer, SourceDataLine> playbackLines = new ConcurrentHashMap<>();
    private final Map<Integer, PlaybackStats> playbackStats = new ConcurrentHashMap<>();
    private volatile CaptureSession captureSession;
    private volatile boolean playbackErrorNotified;

    VoiceChatManager(NetworkClient network, Listener listener) {
        this.network = network;
        this.listener = listener;
    }

    synchronized void start(String key, boolean group, UserInfo receiver, GroupInfo groupInfo) throws LineUnavailableException {
        if (group && groupInfo == null) {
            throw new IllegalArgumentException("Chua chon group voice");
        }
        if (!group && receiver == null) {
            throw new IllegalArgumentException("Chua chon nguoi nhan voice");
        }
        stop();

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT, FRAME_BYTES * 4);
        line.start();

        CaptureSession session = new CaptureSession(key, line);
        Thread thread = new Thread(() -> captureLoop(session, group, receiver, groupInfo), "voice-capture");
        thread.setDaemon(true);
        captureSession = session;
        thread.start();
    }

    synchronized void stop() {
        CaptureSession session = captureSession;
        captureSession = null;
        if (session != null) {
            session.running = false;
            session.line.stop();
            session.line.close();
        }
    }

    boolean isRecording(String key) {
        CaptureSession session = captureSession;
        return session != null && session.running && session.key.equals(key);
    }

    void play(UserInfo sender, byte[] audioData) {
        if (sender == null || audioData == null || audioData.length == 0) {
            return;
        }
        try {
            SourceDataLine line = playbackLine(sender.id());
            line.write(audioData, 0, audioData.length);
            PlaybackStats stats = playbackStats.computeIfAbsent(sender.id(), ignored -> new PlaybackStats());
            stats.frames++;
            stats.bytes += audioData.length;
            if (stats.frames % STATUS_EVERY_FRAMES == 0) {
                notifyReceived(sender, stats.frames, stats.bytes);
            }
        } catch (Exception e) {
            closePlaybackLine(sender.id());
            if (!playbackErrorNotified) {
                playbackErrorNotified = true;
                notifyError("Khong phat duoc voice: " + e.getMessage());
            }
        }
    }

    void shutdown() {
        stop();
        for (SourceDataLine line : playbackLines.values()) {
            line.flush();
            line.stop();
            line.close();
        }
        playbackLines.clear();
        playbackStats.clear();
        playbackErrorNotified = false;
    }

    private void captureLoop(CaptureSession session, boolean group, UserInfo receiver, GroupInfo groupInfo) {
        byte[] buffer = new byte[FRAME_BYTES];
        try {
            while (session.running && network.isConnected()) {
                int read = session.line.read(buffer, 0, buffer.length);
                if (read <= 0) {
                    continue;
                }
                byte[] payload = read == buffer.length ? buffer.clone() : Arrays.copyOf(buffer, read);
                ChatPacket packet = ChatPacket.of(group ? PacketType.VOICE_GROUP : PacketType.VOICE_DIRECT)
                        .put("audioData", payload);
                if (group) {
                    packet.put("group", groupInfo);
                } else {
                    packet.put("receiver", receiver);
                }
                network.send(packet);
                session.sentFrames++;
                session.sentBytes += payload.length;
                if (session.sentFrames % STATUS_EVERY_FRAMES == 0) {
                    notifySent(session.key, session.sentFrames, session.sentBytes);
                }
            }
        } catch (Exception e) {
            if (session.running) {
                notifyError("Voice chat bi dung: " + e.getMessage());
            }
        } finally {
            if (captureSession == session) {
                captureSession = null;
            }
            session.running = false;
            if (session.sentFrames > 0) {
                notifySent(session.key, session.sentFrames, session.sentBytes);
            }
            session.line.close();
        }
    }

    private synchronized SourceDataLine playbackLine(int senderId) throws LineUnavailableException {
        SourceDataLine existing = playbackLines.get(senderId);
        if (existing != null && existing.isOpen()) {
            return existing;
        }
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(FORMAT, FRAME_BYTES * 8);
        line.start();
        SourceDataLine previous = playbackLines.put(senderId, line);
        if (previous != null && previous != line) {
            previous.close();
        }
        return line;
    }

    private void closePlaybackLine(int senderId) {
        SourceDataLine line = playbackLines.remove(senderId);
        if (line != null) {
            line.close();
        }
    }

    private void notifyError(String message) {
        if (listener != null) {
            listener.onError(message);
        }
    }

    private void notifySent(String key, long frames, long bytes) {
        if (listener != null) {
            listener.onSent(key, frames, bytes);
        }
    }

    private void notifyReceived(UserInfo sender, long frames, long bytes) {
        if (listener != null) {
            listener.onReceived(sender, frames, bytes);
        }
    }

    interface Listener {
        void onSent(String key, long frames, long bytes);

        void onReceived(UserInfo sender, long frames, long bytes);

        void onError(String message);
    }

    private static final class PlaybackStats {
        private long frames;
        private long bytes;
    }

    private static final class CaptureSession {
        private final String key;
        private final TargetDataLine line;
        private volatile boolean running = true;
        private long sentFrames;
        private long sentBytes;

        private CaptureSession(String key, TargetDataLine line) {
            this.key = key;
            this.line = line;
        }
    }
}
