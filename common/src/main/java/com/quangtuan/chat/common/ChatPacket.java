package com.quangtuan.chat.common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ChatPacket implements Serializable {
    private final PacketType type;
    private final Map<String, Object> data = new HashMap<>();

    public ChatPacket(PacketType type) {
        this.type = type;
    }

    public static ChatPacket of(PacketType type) {
        return new ChatPacket(type);
    }

    public ChatPacket put(String key, Object value) {
        data.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public String text(String key) {
        Object value = data.get(key);
        return value == null ? "" : value.toString();
    }

    public PacketType type() {
        return type;
    }

    public Map<String, Object> data() {
        return data;
    }
}
