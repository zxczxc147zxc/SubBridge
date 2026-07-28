package com.zxczxc147zxc.subbridge.realip.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChannelInitializer.class)
public interface ChannelInitializerInvoker {
    @Invoker("initChannel")
    void invokeInitChannel(Channel ch) throws Exception;
}