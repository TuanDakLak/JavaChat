package com.quangtuan.chat.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

final class ClientConfig {
    private static final Path CONFIG_FILE = projectRoot()
            .resolve("config")
            .resolve("client-servers.properties");
    private static final Path OLD_CONFIG_FILE = Path.of(System.getProperty("user.home"), ".javachat-client.properties");

    private ClientConfig() {
    }

    static Path configFile() {
        return CONFIG_FILE;
    }

    static List<ServerProfile> loadServers() {
        Properties props = new Properties();
        Path source = Files.exists(CONFIG_FILE) ? CONFIG_FILE : OLD_CONFIG_FILE;
        boolean hasConfig = Files.exists(source);
        if (hasConfig) {
            try (InputStream input = Files.newInputStream(source)) {
                props.load(input);
            } catch (IOException ignored) {
            }
        }

        List<ServerProfile> servers = new ArrayList<>();
        int count = parseInt(props.getProperty("server.count"), 0);
        for (int i = 0; i < count; i++) {
            String prefix = "server." + i + ".";
            String name = props.getProperty(prefix + "name", "");
            String host = props.getProperty(prefix + "host", "");
            int port = parseInt(props.getProperty(prefix + "port"), -1);
            try {
                servers.add(new ServerProfile(name, host, port));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (servers.isEmpty() && !hasConfig) {
            servers.add(new ServerProfile("Localhost", "localhost", 5555));
        }
        if (!servers.isEmpty() && OLD_CONFIG_FILE.equals(source)) {
            try {
                saveServers(servers);
            } catch (IOException ignored) {
            }
        }
        return servers;
    }

    static void saveServers(List<ServerProfile> servers) throws IOException {
        Properties props = new Properties();
        props.setProperty("server.count", String.valueOf(servers.size()));
        for (int i = 0; i < servers.size(); i++) {
            ServerProfile server = servers.get(i);
            String prefix = "server." + i + ".";
            props.setProperty(prefix + "name", server.name());
            props.setProperty(prefix + "host", server.host());
            props.setProperty(prefix + "port", String.valueOf(server.port()));
        }
        Files.createDirectories(CONFIG_FILE.getParent());
        try (OutputStream output = Files.newOutputStream(CONFIG_FILE)) {
            props.store(output, "JavaChat client servers");
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
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
