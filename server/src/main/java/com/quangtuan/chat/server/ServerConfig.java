package com.quangtuan.chat.server;

public final class ServerConfig {
    public static final String DB_HOST = value("DB_HOST", "localhost");
    public static final String DB_PORT = value("DB_PORT", "5432");
    public static final String DB_USER = value("DB_USER", "postgres");
    public static final String DB_PASSWORD = value("DB_PASSWORD", "123");
    public static final String DB_NAME = value("DB_NAME", "javaChat");
    public static final int CHAT_PORT = Integer.parseInt(value("CHAT_PORT", "5555"));

    private ServerConfig() {
    }

    public static String jdbcUrl() {
        return "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    }

    private static String value(String key, String fallback) {
        String fromEnv = System.getenv(key);
        return fromEnv == null || fromEnv.isBlank() ? fallback : fromEnv;
    }
}
