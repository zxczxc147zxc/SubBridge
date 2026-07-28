package com.zxczxc147zxc.subbridge.realip.mixin;

import com.zxczxc147zxc.subbridge.realip.RealIpHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerConnectionListener.class)
public class ServerConnectionListenerMixin {

    @Redirect(
        method = "startTcpServerListener",
        at = @At(
            value = "INVOKE",
            target = "Lio/netty/bootstrap/ServerBootstrap;childHandler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/ServerBootstrap;",
            remap = false
        )
    )
    private ServerBootstrap wrapChildHandler(ServerBootstrap bootstrap, ChannelHandler originalHandler) {
        return bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // 1. 先添加我们的处理器
                ch.pipeline().addFirst(new RealIpHandler());

                // 2. 然后调用原始的 initChannel
                if (originalHandler instanceof ChannelInitializerInvoker) {
                    // 方式一：使用 Invoker（推荐）
                    ((ChannelInitializerInvoker) originalHandler).invokeInitChannel(ch);
                } else if (originalHandler instanceof ChannelInitializer) {
                    // 方式二：使用 @Surrogate 或直接强转（不推荐，但作为降级方案）
                    // 正常情况下 originalHandler 就是 ChannelInitializerInvoker 的实例
                    // 因为 Minecraft 的匿名内部类会被 Mixin 处理
                }
            }
        });
    }
}