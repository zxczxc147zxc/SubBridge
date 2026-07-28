package com.zxczxc147zxc.subbridge.realip.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.zxczxc147zxc.subbridge.realip.config.SubBridgeRealIPConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public class RealIPCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        boolean hasSubBridge = FabricLoader.getInstance().isModLoaded("subbridge");

        if (!hasSubBridge) {
            dispatcher.register(Commands.literal("back")
                    .executes(RealIPCommand::executeHub)
            );
        }

        dispatcher.register(Commands.literal("realip")
                .then(Commands.literal("reload")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .executes(RealIPCommand::executeReload)
                )
        );
    }

    private static int executeHub(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }

        SubBridgeRealIPConfig config = SubBridgeRealIPConfig.getInstance();
        String address = config.getHubAddress();
        int port = config.getHubPort();
        String username = player.getScoreboardName();

        MutableComponent broadcast = Component.literal("")
                .append(Component.literal("✦ ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(username).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" 踏上了返回主服的旅程 ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("✦").withStyle(ChatFormatting.LIGHT_PURPLE));
        src.getServer().getPlayerList().broadcastSystemMessage(broadcast, false);

        ClientboundTransferPacket packet = new ClientboundTransferPacket(address, port);
        player.connection.send(packet);
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        try {
            SubBridgeRealIPConfig.reload();
            ctx.getSource().sendSuccess(() -> Component.literal("配置已重新加载"), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("重载失败: " + e.getMessage()));
            return 0;
        }
    }
}
