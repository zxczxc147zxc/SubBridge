package com.zxczxc147zxc.subbridge.realip;

import com.zxczxc147zxc.subbridge.realip.config.SubBridgeRealIPConfig;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import io.netty.util.IllegalReferenceCountException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RealIpHandler extends ChannelInboundHandlerAdapter {
    public static final AttributeKey<String> REAL_IP_KEY = AttributeKey.valueOf("subbridge_real_ip");
    public static final AttributeKey<Boolean> IP_EXTRACTED = AttributeKey.valueOf("subbridge_ip_extracted");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (Boolean.TRUE.equals(ctx.channel().attr(IP_EXTRACTED).get())) {
            ctx.fireChannelRead(msg);
            return;
        }

        InetSocketAddress remoteAddr = (InetSocketAddress) ctx.channel().remoteAddress();
        if (remoteAddr == null) {
            ctx.fireChannelRead(msg);
            return;
        }
        String remoteHost = remoteAddr.getAddress().getHostAddress();
        List<String> trusted = SubBridgeRealIPConfig.getInstance().getTrustedProxies();
        if (!trusted.contains(remoteHost)) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (!(msg instanceof ByteBuf)) {
            ctx.fireChannelRead(msg);
            return;
        }

        ByteBuf buf = (ByteBuf) msg;
        if (buf.refCnt() <= 0 || !buf.isReadable()) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (buf.readableBytes() < 4) {
            ctx.fireChannelRead(msg);
            return;
        }

        int magic;
        try {
            magic = buf.getInt(buf.readerIndex());
        } catch (IndexOutOfBoundsException | IllegalReferenceCountException e) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (magic != 0xCAFEBABE) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            buf.readInt();
            int len = readVarInt(buf);
            if (len <= 0 || len > 45 || buf.readableBytes() < len) {
                ctx.fireChannelRead(buf);
                return;
            }

            byte[] ipBytes = new byte[len];
            buf.readBytes(ipBytes);
            String realIp = new String(ipBytes, StandardCharsets.UTF_8);

            ctx.channel().attr(REAL_IP_KEY).set(realIp);
            ctx.channel().attr(IP_EXTRACTED).set(true);

            if (buf.readableBytes() > 0) {
                ctx.fireChannelRead(buf);
            }
        } catch (Exception e) {
            if (buf.readableBytes() > 0) {
                ctx.fireChannelRead(buf);
            }
        }
    }

    private int readVarInt(ByteBuf buf) {
        int value = 0;
        int shift = 0;
        for (int i = 0; i < 5; i++) {
            if (!buf.isReadable()) return -1;
            byte b = buf.readByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
        }
        return -1;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("Connection reset") ||
                msg.contains("Broken pipe") ||
                msg.contains("An existing connection was forcibly closed") ||
                msg.contains("Connection closed by remote host"))) {
                return;
            }
        }
        // 不打印任何日志
    }
}