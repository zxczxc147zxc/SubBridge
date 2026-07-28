package com.zxczxc147zxc.subbridge.realip.mixin;

import com.zxczxc147zxc.subbridge.realip.RealIpHandler;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;

@Mixin(ServerLoginPacketListenerImpl.class)
public class ServerLoginPacketListenerMixin {

    @Shadow @Final private Connection connection;

    @Inject(method = "handleHello", at = @At("HEAD"))
    private void onHandleHello(ServerboundHelloPacket packet, CallbackInfo ci) {
        Channel channel = ((ConnectionAccessor) connection).getChannel();
        if (channel == null) return;

        String realIp = channel.attr(RealIpHandler.REAL_IP_KEY).get();
        if (realIp == null) return;

        try {
            InetSocketAddress newAddr = new InetSocketAddress(
                realIp,
                ((InetSocketAddress) connection.getRemoteAddress()).getPort()
            );
            ((ConnectionAccessor) connection).setAddress(newAddr);
        } catch (Exception e) {
            // 异常静默忽略，不输出日志
        }
    }
}