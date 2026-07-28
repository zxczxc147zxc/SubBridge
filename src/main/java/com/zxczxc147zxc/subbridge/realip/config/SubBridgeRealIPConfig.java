package com.zxczxc147zxc.subbridge.realip.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zxczxc147zxc.subbridge.realip.SubBridgeRealIP;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class SubBridgeRealIPConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SubBridgeRealIPConfig instance;

    private String hubAddress = "127.0.0.1";
    private int hubPort = 25566;
    private List<String> trustedProxies = Arrays.asList("127.0.0.1", "::1");

    public String getHubAddress() { return hubAddress; }
    public void setHubAddress(String hubAddress) { this.hubAddress = hubAddress; }
    public int getHubPort() { return hubPort; }
    public void setHubPort(int hubPort) { this.hubPort = hubPort; }
    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> trustedProxies) { this.trustedProxies = trustedProxies; }

    public static SubBridgeRealIPConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static SubBridgeRealIPConfig load() {
        Path configDir = Paths.get("config", "subbridge-realip");
        Path configFile = configDir.resolve("config.json");
        try {
            if (!Files.exists(configFile)) {
                Files.createDirectories(configDir);
                SubBridgeRealIPConfig defaults = new SubBridgeRealIPConfig();
                String json = GSON.toJson(defaults);
                Files.write(configFile, json.getBytes());
                SubBridgeRealIP.LOGGER.info("[SubBridge-RealIP] created default config: {}", configFile);
                instance = defaults;
                return defaults;
            }
            String json = new String(Files.readAllBytes(configFile));
            instance = GSON.fromJson(json, SubBridgeRealIPConfig.class);
            if (instance == null) {
                instance = new SubBridgeRealIPConfig();
            }
            SubBridgeRealIP.LOGGER.info("[SubBridge-RealIP] config loaded: hubAddress={}, hubPort={}", instance.hubAddress, instance.hubPort);
            return instance;
        } catch (IOException e) {
            SubBridgeRealIP.LOGGER.error("[SubBridge-RealIP] failed to load config", e);
            instance = new SubBridgeRealIPConfig();
            return instance;
        }
    }

    public static void reload() {
        load();
        SubBridgeRealIP.LOGGER.info("[SubBridge-RealIP] config reloaded");
    }
}
