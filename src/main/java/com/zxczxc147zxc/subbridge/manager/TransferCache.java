package com.zxczxc147zxc.subbridge.manager;

import com.zxczxc147zxc.subbridge.SubBridgeMod;
import com.zxczxc147zxc.subbridge.config.ServerEntry;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TransferCache {
    private static final ConcurrentHashMap<UUID, TransferRequest> uuidCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, TransferRequest> usernameCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, TransferRequest> ipCache = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    private static int timeoutSeconds = 1; // 默认 1 秒

    static {
        startCleaner();
    }

    public static void setTimeout(int seconds) {
        timeoutSeconds = seconds;
    }

    private static void startCleaner() {
        cleaner.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long timeoutMillis = timeoutSeconds * 1000L;
            uuidCache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > timeoutMillis);
            usernameCache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > timeoutMillis);
            ipCache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > timeoutMillis);
        }, 5, 5, TimeUnit.SECONDS);
    }

    // ---- 存储 ----
    public static void put(UUID uuid, ServerEntry target) {
        uuidCache.put(uuid, new TransferRequest(target, System.currentTimeMillis()));
    }

    public static void putByUsername(String username, ServerEntry target) {
        usernameCache.put(username, new TransferRequest(target, System.currentTimeMillis()));
    }

    public static void putByIp(String ip, ServerEntry target) {
        ipCache.put(ip, new TransferRequest(target, System.currentTimeMillis()));
    }

    // ---- 查询 ----
    public static TransferRequest peek(UUID uuid) {
        return uuidCache.get(uuid);
    }

    public static TransferRequest peekByUsername(String username) {
        return usernameCache.get(username);
    }

    public static TransferRequest peekByIp(String ip) {
        return ipCache.get(ip);
    }

    // ---- 获取并删除 ----
    public static TransferRequest getAndRemove(UUID uuid) {
        TransferRequest req = uuidCache.remove(uuid);
        if (req != null) {
            SubBridgeMod.LOGGER.debug("移除 UUID 缓存: {}", uuid);
        }
        return req;
    }

    public static TransferRequest getAndRemoveByUsername(String username) {
        TransferRequest req = usernameCache.remove(username);
        if (req != null) {
            SubBridgeMod.LOGGER.debug("移除用户名缓存: {}", username);
        }
        return req;
    }

    public static TransferRequest getAndRemoveByIp(String ip) {
        TransferRequest req = ipCache.remove(ip);
        if (req != null) {
            SubBridgeMod.LOGGER.debug("移除 IP 缓存: {}", ip);
        }
        return req;
    }

    public static void shutdown() {
        cleaner.shutdownNow();
    }

    public static class TransferRequest {
        public final ServerEntry target;
        public final long timestamp;

        public TransferRequest(ServerEntry target, long timestamp) {
            this.target = target;
            this.timestamp = timestamp;
        }
    }
}