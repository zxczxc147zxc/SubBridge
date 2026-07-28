package com.zxczxc147zxc.subbridge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zxczxc147zxc.subbridge.SubBridgeMod;
import com.zxczxc147zxc.subbridge.config.ServerEntry;
import com.zxczxc147zxc.subbridge.manager.ServerManager;
import com.zxczxc147zxc.subbridge.manager.TransferCache;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class ServerCommand {

    private static final SuggestionProvider<CommandSourceStack> SERVER_SUGGESTIONS = (context, builder) -> {
        ServerManager.getInstance().loadConfig();
        for (String name : ServerManager.getInstance().getAllServerNames()) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sub")
                .then(Commands.literal("server")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(SERVER_SUGGESTIONS)
                                .executes(ServerCommand::executeServer)
                        )
                )
                .then(Commands.literal("start")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(SERVER_SUGGESTIONS)
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                                .executes(ServerCommand::executeStart)
                        )
                )
                .then(Commands.literal("stop")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(SERVER_SUGGESTIONS)
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                                .executes(ServerCommand::executeStop)
                        )
                )
                .then(Commands.literal("list")
                        .executes(ServerCommand::executeList)
                )
                .then(Commands.literal("reload")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .executes(ServerCommand::executeReload)
                )
                .then(Commands.literal("registerport")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                .executes(ctx -> executeRegisterPort(ctx, null))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> executeRegisterPort(ctx, StringArgumentType.getString(ctx, "name")))
                                )
                        )
                )
                .then(Commands.literal("unregister")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String name : ServerManager.getInstance().getTempServerNames()) {
                                        builder.suggest(name);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ServerCommand::executeUnregister)
                        )
                )
        );
    }

    private static boolean isPortOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static int executeRegisterPort(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack src = ctx.getSource();
        int port = IntegerArgumentType.getInteger(ctx, "port");

        if (!isPortOpen(port)) {
            src.sendFailure(Component.literal("端口 " + port + " 不可达，请确保子服已启动"));
            return 0;
        }

        if (name == null || name.isEmpty()) {
            name = "port_" + port;
        }

        if (ServerManager.getInstance().getServerEntry(name) != null) {
            src.sendFailure(Component.literal("子服名 '" + name + "' 已存在，请使用其他名称"));
            return 0;
        }

        ServerEntry entry = new ServerEntry();
        entry.setName(name);
        entry.setPort(port);
        entry.setPath("");
        entry.setJar("");
        entry.setJvmArgs("");

        ServerManager.getInstance().registerTempServer(name, entry);

        String playerName = src.getPlayer() != null ? src.getPlayer().getScoreboardName() : "Console";
        SubBridgeMod.LOGGER.info("[SubBridge] {} 注册外部子服 {} (端口 {})", playerName, name, port);

        final String finalName = name;
        src.sendSuccess(() -> Component.literal("已注册外部子服 '" + finalName + "'（端口 " + port + "），现在可以使用 /sub server " + finalName + " 跨服"), true);
        return 1;
    }

    private static int executeUnregister(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");

        if (ServerManager.getInstance().removeTempServer(name)) {
            String playerName = src.getPlayer() != null ? src.getPlayer().getScoreboardName() : "Console";
            SubBridgeMod.LOGGER.info("[SubBridge] {} 移除外部子服 {}", playerName, name);
            src.sendSuccess(() -> Component.literal("已移除临时子服 '" + name + "'"), true);
            return 1;
        } else {
            src.sendFailure(Component.literal("未找到临时子服 '" + name + "'（配置文件中定义的不可移除）"));
            return 0;
        }
    }

    private static int executeServer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        ServerManager.getInstance().loadConfig();

        ServerEntry entry = ServerManager.getInstance().getServerEntry(name);
        if (entry == null) {
            src.sendFailure(Component.literal("未知子服: " + name));
            return 0;
        }

        int port = entry.getPort();
        if (!isPortOpen(port)) {
            src.sendFailure(Component.literal("子服未运行或端口未开放: " + name));
            return 0;
        }

        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }

        UUID uuid = player.getUUID();
        String username = player.getScoreboardName();
        String clientIp = player.getIpAddress();
        if ("0:0:0:0:0:0:0:1".equals(clientIp) || "::1".equals(clientIp)) {
            clientIp = "127.0.0.1";
        }

        TransferCache.put(uuid, entry);
        TransferCache.putByUsername(username, entry);
        if (ServerManager.getInstance().getProxyConfig().isEnableIpRouting()) {
            TransferCache.putByIp(clientIp, entry);
        }

        SubBridgeMod.LOGGER.info("[SubBridge] {} 跨服到 {} (IP: {})", username, name, clientIp);

        MutableComponent broadcast = Component.literal("")
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("SubBridge").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(username).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(name).withStyle(ChatFormatting.GREEN));
        src.getServer().getPlayerList().broadcastSystemMessage(broadcast, false);

        String external = ServerManager.getInstance().getProxyConfig().getExternalAddress();
        int externalPort = ServerManager.getInstance().getProxyConfig().getExternalPort();
        ClientboundTransferPacket packet = new ClientboundTransferPacket(external, externalPort);
        player.connection.send(packet);

        src.sendSuccess(() -> Component.literal("正在传送到 " + name + "..."), false);
        return 1;
    }

    private static int executeStart(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack src = ctx.getSource();

        try {
            ServerManager.getInstance().loadConfig();
            ServerManager.getInstance().startServer(name);

            String playerName = src.getPlayer() != null ? src.getPlayer().getScoreboardName() : "Console";
            SubBridgeMod.LOGGER.info("[SubBridge] {} 启动子服 {}", playerName, name);
            src.sendSuccess(() -> Component.literal("正在启动子服 " + name + "，请稍候..."), true);

            new Thread(() -> {
                try {
                    int maxWait = 120;
                    int waited = 0;
                    while (!ServerManager.getInstance().isReady(name) && waited < maxWait) {
                        Thread.sleep(1000);
                        waited++;
                        if (!ServerManager.getInstance().isRunning(name)) {
                            src.sendSuccess(() -> Component.literal("子服 " + name + " 启动失败，进程已退出"), false);
                            return;
                        }
                    }
                    if (ServerManager.getInstance().isReady(name)) {
                        src.sendSuccess(() -> Component.literal("子服 " + name + " 已就绪"), false);
                    } else {
                        src.sendSuccess(() -> Component.literal("子服 " + name + " 启动超时 (2 分钟)，请检查日志"), false);
                    }
                } catch (InterruptedException ignored) {}
            }).start();

            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("启动失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack src = ctx.getSource();

        try {
            ServerManager.getInstance().loadConfig();
            ServerManager.getInstance().stopServer(name);
            String playerName = src.getPlayer() != null ? src.getPlayer().getScoreboardName() : "Console";
            SubBridgeMod.LOGGER.info("[SubBridge] {} 停止子服 {}", playerName, name);
            src.sendSuccess(() -> Component.literal("子服 " + name + " 已停止"), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("停止失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        ServerManager.getInstance().loadConfig();
        Map<String, Boolean> status = ServerManager.getInstance().getAllStatus();

        if (status.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("没有配置任何子服。"), false);
            return 1;
        }

        MutableComponent message = Component.literal("子服列表:\n");
        for (Map.Entry<String, Boolean> e : status.entrySet()) {
            String name = e.getKey();
            boolean running = e.getValue();

            Component state = running
                    ? Component.literal("运行中").withStyle(ChatFormatting.GREEN)
                    : Component.literal("已停止").withStyle(ChatFormatting.RED);

            message.append(Component.literal(" - " + name + ": "))
                   .append(state)
                   .append(Component.literal("\n"));
        }
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerManager.getInstance().reloadConfig();
            TransferCache.setTimeout(ServerManager.getInstance().getProxyConfig().getCacheTimeoutSeconds());
            ctx.getSource().sendSuccess(() -> Component.literal("配置已重新加载，缓存超时: " +
                    ServerManager.getInstance().getProxyConfig().getCacheTimeoutSeconds() + " 秒"), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("重载失败: " + e.getMessage()));
            return 0;
        }
    }
}