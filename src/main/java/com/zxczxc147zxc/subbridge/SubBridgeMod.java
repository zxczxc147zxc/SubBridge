package com.zxczxc147zxc.subbridge;

import com.zxczxc147zxc.subbridge.command.ServerCommand;
import com.zxczxc147zxc.subbridge.manager.ServerManager;
import com.zxczxc147zxc.subbridge.manager.TransferCache;
import com.zxczxc147zxc.subbridge.network.ProxyServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SubBridgeMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("SubBridge");
    private static ProxyServer proxyServer;

    @Override
    public void onInitialize() {
        LOGGER.info("[SubBridge] 初始化...");

        ServerManager.getInstance().loadConfig();

        new Thread(() -> {
            try {
                int proxyPort = ServerManager.getInstance().getProxyConfig().getProxyPort();
                proxyServer = new ProxyServer(proxyPort);
                proxyServer.start();
                LOGGER.info("[SubBridge] 代理已启动，监听端口 {}", proxyPort);
            } catch (Exception e) {
                LOGGER.error("[SubBridge] 代理启动失败: {}", e.getMessage());
            }
        }).start();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ServerCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        LOGGER.info("[SubBridge] 初始化完成");
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("[SubBridge] 正在关闭...");

        ServerManager.getInstance().stopAllServers();

        if (proxyServer != null) {
            proxyServer.shutdown();
        }

        TransferCache.shutdown();
        ServerManager.getInstance().shutdownExecutor();

        LOGGER.info("[SubBridge] 已关闭");
    }
}