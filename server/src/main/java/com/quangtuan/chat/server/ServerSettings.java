package com.quangtuan.chat.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record ServerSettings(
        String dbHost,
        String dbPort,
        String dbUser,
        String dbPassword,
        String dbName,
        int chatPort
) {
    private static final Path CONFIG_FILE = projectRoot()
            .resolve("config")
            .resolve("server.properties");

    public ServerSettings {
        dbHost = normalize(dbHost, "localhost");
        dbPort = normalize(dbPort, "5432");
        dbUser = normalize(dbUser, "postgres");
        dbPassword = dbPassword == null ? "" : dbPassword;
        dbName = normalize(dbName, "javaChat");
        if (chatPort < 1 || chatPort > 65535) {
            throw new IllegalArgumentException("CHAT_PORT phai nam trong khoang 1-65535");
        }
    }

    public static Path configFile() {
        return CONFIG_FILE;
    }

    public static ServerSettings load() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (InputStream input = Files.newInputStream(CONFIG_FILE)) {
                props.load(input);
            } catch (IOException ignored) {
            }
        }

        return new ServerSettings(
                value(props, "DB_HOST", "localhost"),
                value(props, "DB_PORT", "5432"),
                value(props, "DB_USER", "postgres"),
                passwordValue(props),
                value(props, "DB_NAME", "javaChat"),
                parseInt(value(props, "CHAT_PORT", "5555"), 5555)
        );
    }

    public void save() throws IOException {
        Properties props = new Properties();
        props.setProperty("CHAT_PORT", String.valueOf(chatPort));
        props.setProperty("DB_HOST", dbHost);
        props.setProperty("DB_PORT", dbPort);
        props.setProperty("DB_USER", dbUser);
        props.setProperty("DB_PASSWORD", dbPassword);
        props.setProperty("DB_NAME", dbName);

        Files.createDirectories(CONFIG_FILE.getParent());
        try (OutputStream output = Files.newOutputStream(CONFIG_FILE)) {
            props.store(output, "JavaChat server config");
        }
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }

    private static String value(Properties props, String key, String fallback) {
        String fromFile = props.getProperty(key);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile;
        }
        String fromEnv = System.getenv(key);
        return fromEnv == null || fromEnv.isBlank() ? fallback : fromEnv;
    }

    private static String passwordValue(Properties props) {
        if (props.containsKey("DB_PASSWORD")) {
            return props.getProperty("DB_PASSWORD", "");
        }
        String fromEnv = System.getenv("DB_PASSWORD");
        return fromEnv == null ? "123" : fromEnv;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path path = current; path != null; path = path.getParent()) {
            if (Files.exists(path.resolve("pom.xml"))
                    && Files.isDirectory(path.resolve("client"))
                    && Files.isDirectory(path.resolve("server"))) {
                return path;
            }
        }
        return current;
    }
}
