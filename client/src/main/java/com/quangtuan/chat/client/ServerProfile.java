package com.quangtuan.chat.client;

record ServerProfile(String name, String host, int port) {
    ServerProfile {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host khong duoc rong");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port phai nam trong khoang 1-65535");
        }
        host = host.trim();
        name = name == null || name.isBlank() ? host + ":" + port : name.trim();
    }

    boolean sameEndpoint(ServerProfile other) {
        return other != null && host.equalsIgnoreCase(other.host) && port == other.port;
    }

    String endpoint() {
        return host + ":" + port;
    }

    @Override
    public String toString() {
        return name + " (" + endpoint() + ")";
    }
}
