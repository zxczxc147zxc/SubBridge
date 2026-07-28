package com.zxczxc147zxc.subbridge.realip;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储 UUID → 真实 IP 的映射，用于在 Login 阶段将真实 IP 注入到 Connection
 */
public class RealIpManager {
    private static final ConcurrentHashMap<UUID, String> REAL_IP_MAP = new ConcurrentHashMap<>();

    public static void put(UUID uuid, String ip) {
        if (uuid != null && ip != null) {
            REAL_IP_MAP.put(uuid, ip);
        }
    }

    public static String pop(UUID uuid) {
        if (uuid == null) return null;
        return REAL_IP_MAP.remove(uuid);
    }
}