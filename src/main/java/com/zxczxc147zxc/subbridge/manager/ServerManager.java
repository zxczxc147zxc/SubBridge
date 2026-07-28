package com.zxczxc147zxc.subbridge.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.zxczxc147zxc.subbridge.SubBridgeMod;
import com.zxczxc147zxc.subbridge.config.ProxyConfig;
import com.zxczxc147zxc.subbridge.config.ServerEntry;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerManager {
    private static final ServerManager INSTANCE = new ServerManager();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private ProxyConfig proxyConfig;
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Map<String, ServerEntry> entryMap = new HashMap<>();
    private final Map<String, Boolean> serverReadyMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Future<?>> detectionTasks = new ConcurrentHashMap<>();
    private final Map<String, ServerEntry> tempEntries = new ConcurrentHashMap<>();

    private ServerManager() {}

    public static ServerManager getInstance() {
        return INSTANCE;
    }

    public void shutdownExecutor() {
        for (Future<?> task : detectionTasks.values()) {
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }
        }
        detectionTasks.clear();
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                SubBridgeMod.LOGGER.debug("[SubBridge] executor forced shutdown");
            }
        } catch (InterruptedException ignored) {}
    }

    public void loadConfig() {
        loadProxyConfig();
        loadServerConfig();
    }

    private void loadProxyConfig() {
        Path configDir = Paths.get("config", "subbridge");
        Path configFile = configDir.resolve("ProxyServer.txt");
        try {
            if (!Files.exists(configFile)) {
                Files.createDirectories(configDir);
                String defaultConfig = "# SubBridge 代理配置文件\n" +
                        "# 外部地址 (玩家连接的域名或IP，用于Transfer包)\n" +
                        "externalAddress=127.0.0.1\n" +
                        "# 代理监听端口\n" +
                        "proxyPort=25566\n" +
                        "# 缓存超时时间 (秒)\n" +
                        "cacheTimeoutSeconds=1\n" +
                        "# 是否启用 IP 路由（默认 true）\n" +
                        "enableIpRouting=true\n" +
                        "# 主服端口（0 表示自动获取）\n" +
                        "mainServerPort=0\n" +
                        "# 路由模式: ip_mode | strict_ip\n" +
                        "routeMode=ip_mode\n" +
                        "# 公网映射端口（若与 proxyPort 不同，请填写映射后的公网端口，例如 28247）\n" +
                        "# 若不设置或设为 0，则使用 proxyPort\n" +
                        "externalPort=0\n" +
                        "# 是否启用 PROXY Protocol 支持（仅当使用内网穿透且开启 PROXY 时设为 true）\n" +
                        "enableProxyProtocol=false\n";
                Files.write(configFile, defaultConfig.getBytes());
                SubBridgeMod.LOGGER.info("[SubBridge] created default proxy config: {}", configFile);
            }

            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(configFile)) {
                props.load(is);
            }

            proxyConfig = new ProxyConfig();
            proxyConfig.setExternalAddress(props.getProperty("externalAddress", "127.0.0.1"));
            proxyConfig.setProxyPort(Integer.parseInt(props.getProperty("proxyPort", "25566")));
            proxyConfig.setCacheTimeoutSeconds(Integer.parseInt(props.getProperty("cacheTimeoutSeconds", "1")));
            proxyConfig.setEnableIpRouting(Boolean.parseBoolean(props.getProperty("enableIpRouting", "true")));
            proxyConfig.setMainServerPort(Integer.parseInt(props.getProperty("mainServerPort", "0")));
            proxyConfig.setRouteMode(props.getProperty("routeMode", "ip_mode"));
            proxyConfig.setExternalPort(Integer.parseInt(props.getProperty("externalPort", "0")));
            // ★★★ 新增：读取 PROXY Protocol 开关
            proxyConfig.setEnableProxyProtocol(Boolean.parseBoolean(props.getProperty("enableProxyProtocol", "false")));

        } catch (IOException e) {
            SubBridgeMod.LOGGER.error("[SubBridge] failed to load proxy config", e);
            proxyConfig = new ProxyConfig();
        }
    }

    private void loadServerConfig() {
        Path configDir = Paths.get("config", "subbridge");
        Path configFile = configDir.resolve("servers.json");
        try {
            if (!Files.exists(configFile)) {
                Files.createDirectories(configDir);
                ServerEntry example = new ServerEntry();
                example.setName("example");
                example.setPort(30001);
                example.setPath("./subservers/example");
                example.setJar("server.jar");
                example.setJvmArgs("-Xmx1024M");

                List<ServerEntry> servers = new ArrayList<>();
                servers.add(example);
                String json = gson.toJson(servers);
                Files.write(configFile, json.getBytes());
                SubBridgeMod.LOGGER.info("[SubBridge] created default server config: {}", configFile);
            }

            String json = new String(Files.readAllBytes(configFile));
            ServerEntry[] entries;

            try {
                entries = gson.fromJson(json, ServerEntry[].class);
            } catch (Exception e) {
                try {
                    JsonObject obj = gson.fromJson(json, JsonObject.class);
                    if (obj.has("servers")) {
                        entries = gson.fromJson(obj.get("servers"), ServerEntry[].class);
                    } else {
                        throw new IllegalStateException("JSON 对象没有 'servers' 字段");
                    }
                } catch (Exception ex) {
                    SubBridgeMod.LOGGER.error("[SubBridge] failed to parse servers.json", ex);
                    entryMap.clear();
                    return;
                }
            }

            entryMap.clear();
            for (ServerEntry entry : entries) {
                entryMap.put(entry.getName(), entry);
            }
            SubBridgeMod.LOGGER.debug("[SubBridge] loaded {} server entries", entryMap.size());

        } catch (IOException e) {
            SubBridgeMod.LOGGER.error("[SubBridge] failed to load server config", e);
        }

        TransferCache.setTimeout(proxyConfig.getCacheTimeoutSeconds());
    }

    public void reloadConfig() {
        loadConfig();
        SubBridgeMod.LOGGER.info("[SubBridge] configuration reloaded");
    }

    public void registerTempServer(String name, ServerEntry entry) {
        tempEntries.put(name, entry);
    }

    public boolean removeTempServer(String name) {
        return tempEntries.remove(name) != null;
    }

    public Set<String> getTempServerNames() {
        return tempEntries.keySet();
    }

    public ServerEntry getServerEntry(String name) {
        if (tempEntries.containsKey(name)) {
            return tempEntries.get(name);
        }
        return entryMap.get(name);
    }

    public Collection<String> getAllServerNames() {
        Set<String> all = new HashSet<>(entryMap.keySet());
        all.addAll(tempEntries.keySet());
        return all;
    }

    public Map<String, Boolean> getAllStatus() {
        Map<String, Boolean> status = new HashMap<>();
        for (String name : entryMap.keySet()) {
            status.put(name, isRunning(name) && isReady(name));
        }
        for (Map.Entry<String, ServerEntry> entry : tempEntries.entrySet()) {
            int port = entry.getValue().getPort();
            boolean running = false;
            try (Socket s = new Socket()) {
                s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                running = true;
            } catch (IOException ignored) {}
            status.put(entry.getKey(), running);
        }
        return status;
    }

    public ProxyConfig getProxyConfig() {
        return proxyConfig;
    }

    public Collection<ServerEntry> getAllEntries() {
        return entryMap.values();
    }

    public boolean isRunning(String name) {
        Process p = runningProcesses.get(name);
        if (p != null && p.isAlive()) {
            return true;
        }
        ServerEntry tempEntry = tempEntries.get(name);
        if (tempEntry != null) {
            int port = tempEntry.getPort();
            try (Socket s = new Socket()) {
                s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    public boolean isReady(String name) {
        return serverReadyMap.getOrDefault(name, false);
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void setupServerProperties(ServerEntry entry) throws IOException {
        Path serverDir = Paths.get(entry.getPath());
        Files.createDirectories(serverDir);

        Path propsPath = serverDir.resolve("server.properties");
        Properties props = new Properties();
        if (Files.exists(propsPath)) {
            try (InputStream is = Files.newInputStream(propsPath)) {
                props.load(is);
            }
        }
        props.setProperty("online-mode", "false");
        props.setProperty("server-ip", "127.0.0.1");
        props.setProperty("server-port", String.valueOf(entry.getPort()));
        props.setProperty("accepts-transfers", "true");

        try (OutputStream os = Files.newOutputStream(propsPath)) {
            props.store(os, "Auto-generated by SubBridge");
        }

        Path eulaPath = serverDir.resolve("eula.txt");
        if (!Files.exists(eulaPath)) {
            Files.write(eulaPath, "eula=true".getBytes());
        }
    }

    public void startServer(String name) throws Exception {
        ServerEntry entry = entryMap.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("未知子服: " + name);
        }
        if (isRunning(name)) {
            throw new IllegalStateException("子服已在运行: " + name);
        }
        if (!isPortAvailable(entry.getPort())) {
            throw new IOException("端口 " + entry.getPort() + " 已被占用");
        }

        setupServerProperties(entry);

        Path serverDir = Paths.get(entry.getPath());
        ProcessBuilder pb;
        String startCmd = entry.getStartCommand();

        if (startCmd != null && !startCmd.isEmpty()) {
            String os = System.getProperty("os.name").toLowerCase();
            boolean isWindows = os.contains("win");

            if (startCmd.endsWith(".ps1") && isWindows) {
                pb = new ProcessBuilder(
                    "cmd", "/c", "start",
                    "\"" + entry.getName() + "\"",
                    "powershell", "-ExecutionPolicy", "Bypass", "-NoExit", "-File", startCmd
                );
                pb.redirectErrorStream(false);
            } else if (startCmd.endsWith(".ps1")) {
                pb = new ProcessBuilder("pwsh", "-ExecutionPolicy", "Bypass", "-File", startCmd);
            } else if (startCmd.endsWith(".sh") || startCmd.endsWith(".bat")) {
                pb = new ProcessBuilder(startCmd);
            } else if (isWindows) {
                pb = new ProcessBuilder("cmd", "/c", startCmd);
            } else {
                pb = new ProcessBuilder("sh", "-c", startCmd);
            }
        } else {
            String javaCmd = System.getProperty("java.home") + "/bin/java";
            String jvmArgs = entry.getJvmArgs() != null ? entry.getJvmArgs() : "";
            String jar = entry.getJar();
            pb = new ProcessBuilder(javaCmd, jvmArgs, "-jar", jar, "nogui");
        }

        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    SubBridgeMod.LOGGER.debug("[{}] {}", name, line);
                }
            } catch (IOException e) {
                // 流关闭时正常退出
            }
        });
        outputReader.setDaemon(true);
        outputReader.start();

        runningProcesses.put(name, process);
        serverReadyMap.put(name, false);

        Thread monitor = new Thread(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException ignored) {}
            runningProcesses.remove(name);
            serverReadyMap.remove(name);
            SubBridgeMod.LOGGER.debug("[SubBridge] {} exited", name);
        });
        monitor.setDaemon(true);
        monitor.start();

        Future<?> task = executor.submit(() -> {
            int port = entry.getPort();
            int maxWaitSeconds = 120;
            int waited = 0;
            SubBridgeMod.LOGGER.info("[SubBridge] waiting for {} port {} ... (max {}s)", name, port, maxWaitSeconds);

            while (waited < maxWaitSeconds) {
                if (Thread.currentThread().isInterrupted()) {
                    SubBridgeMod.LOGGER.debug("[SubBridge] port detection for {} interrupted", name);
                    return;
                }

                Process p = runningProcesses.get(name);
                if (p == null || !p.isAlive()) {
                    SubBridgeMod.LOGGER.warn("[SubBridge] {} process exited during startup", name);
                    serverReadyMap.put(name, false);
                    return;
                }

                try (Socket s = new Socket()) {
                    s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
                    serverReadyMap.put(name, true);
                    SubBridgeMod.LOGGER.info("[SubBridge] {} is ready on port {}", name, port);
                    return;
                } catch (IOException ignored) {}

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    SubBridgeMod.LOGGER.debug("[SubBridge] port detection for {} interrupted", name);
                    return;
                }
                waited++;
            }

            SubBridgeMod.LOGGER.warn("[SubBridge] {} startup timeout ({}s)", name, maxWaitSeconds);
            serverReadyMap.put(name, false);
        });
        detectionTasks.put(name, task);
    }

    public void stopServer(String name) throws Exception {
        Future<?> task = detectionTasks.remove(name);
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }

        Process p = runningProcesses.get(name);
        if (p == null || !p.isAlive()) {
            runningProcesses.remove(name);
            serverReadyMap.remove(name);
            return;
        }

        ServerEntry entry = entryMap.get(name);
        if (entry == null) {
            p.destroy();
            runningProcesses.remove(name);
            serverReadyMap.remove(name);
            SubBridgeMod.LOGGER.warn("[SubBridge] {} terminated (config missing)", name);
            return;
        }

        try {
            OutputStream stdin = p.getOutputStream();
            stdin.write("stop\n".getBytes());
            stdin.flush();
        } catch (Exception ignored) {}

        try {
            boolean exited = p.waitFor(30, TimeUnit.SECONDS);
            if (exited) {
                runningProcesses.remove(name);
                serverReadyMap.remove(name);
                SubBridgeMod.LOGGER.info("[SubBridge] {} stopped", name);
                return;
            }
        } catch (InterruptedException ignored) {}

        if (p.isAlive()) {
            p.destroy();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
        }
        if (p.isAlive()) {
            p.destroyForcibly();
        }

        runningProcesses.remove(name);
        serverReadyMap.remove(name);
        SubBridgeMod.LOGGER.warn("[SubBridge] {} force stopped", name);
    }

    public void stopAllServers() {
        SubBridgeMod.LOGGER.info("[SubBridge] stopping all servers...");

        for (Map.Entry<String, Future<?>> entry : detectionTasks.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isDone()) {
                entry.getValue().cancel(true);
            }
        }
        detectionTasks.clear();

        List<String> names = new ArrayList<>(runningProcesses.keySet());

        for (String name : names) {
            try {
                stopServer(name);
            } catch (Exception e) {
                SubBridgeMod.LOGGER.warn("[SubBridge] error stopping {}: {}", name, e.getMessage());
                Process p = runningProcesses.remove(name);
                if (p != null && p.isAlive()) {
                    p.destroyForcibly();
                }
                serverReadyMap.remove(name);
            }
        }

        for (Process p : runningProcesses.values()) {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
        runningProcesses.clear();
        serverReadyMap.clear();
        SubBridgeMod.LOGGER.info("[SubBridge] all servers stopped");
    }

    public int getEffectiveMainServerPort() {
        int configured = proxyConfig.getMainServerPort();
        if (configured > 0) {
            return configured;
        }
        try {
            Path propsPath = Paths.get("server.properties");
            Properties props = new Properties();
            if (Files.exists(propsPath)) {
                try (InputStream is = Files.newInputStream(propsPath)) {
                    props.load(is);
                }
                String portStr = props.getProperty("server-port", "25565");
                return Integer.parseInt(portStr);
            }
        } catch (Exception e) {
            SubBridgeMod.LOGGER.warn("[SubBridge] failed to read server.properties, using default port 25565");
        }
        return 25565;
    }
}