package com.quangtuan.chat.server;

public final class ServerConfig {
    private static final ServerSettings SETTINGS = ServerSettings.load();

    public static final String DB_HOST = SETTINGS.dbHost();
    public static final String DB_PORT = SETTINGS.dbPort();
    public static final String DB_USER = SETTINGS.dbUser();
    public static final String DB_PASSWORD = SETTINGS.dbPassword();
    public static final String DB_NAME = SETTINGS.dbName();
    public static final int CHAT_PORT = SETTINGS.chatPort();

    private ServerConfig() {
    }

    public static String jdbcUrl() {
        return SETTINGS.jdbcUrl();
    }
}
