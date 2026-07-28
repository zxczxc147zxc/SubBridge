package com.zxczxc147zxc.subbridge.network;

import com.zxczxc147zxc.subbridge.SubBridgeMod;
import com.zxczxc147zxc.subbridge.manager.ServerManager;
import com.zxczxc147zxc.subbridge.manager.TransferCache;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.util.AttributeKey;
import net.minecraft.network.FriendlyByteBuf;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProxyServer {
    private final int proxyPort;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public ProxyServer(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        if (ServerManager.getInstance().getProxyConfig().isEnableProxyProtocol()) {
                            pipeline.addLast(new HAProxyMessageDecoder());
                        }
                        pipeline.addLast(new ProxyFrontendHandler(getMainServerPort()));
                    }
                })
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.AUTO_READ, true);

        try {
            serverChannel = b.bind(proxyPort).sync().channel();
            SubBridgeMod.LOGGER.info("[SubBridge] proxy server started on port {}", proxyPort);
        } catch (Exception e) {
            if (e.getCause() instanceof BindException) {
                throw new Exception("port " + proxyPort + " is already in use");
            }
            throw e;
        }
    }

    private int getMainServerPort() {
        return ServerManager.getInstance().getEffectiveMainServerPort();
    }

    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        SubBridgeMod.LOGGER.info("[SubBridge] proxy server stopped");
    }

    private static final class AttributeKeys {
        static final AttributeKey<String> CLIENT_IP = AttributeKey.valueOf("subbridge_client_ip");
    }

    private static class ProxyFrontendHandler extends ChannelInboundHandlerAdapter {
        private enum State { HANDSHAKE, STATUS, LOGIN, FORWARD }

        private final int mainServerPort;
        private State state = State.HANDSHAKE;
        private final List<byte[]> packetList = new ArrayList<>();
        private ByteBuf pendingBuffer = Unpooled.buffer();
        private int targetPort;
        private boolean decided = false;
        private String clientIp;
        private Channel backendChannel;
        private boolean backendConnected = false;

        public ProxyFrontendHandler(int mainServerPort) {
            this.mainServerPort = mainServerPort;
            this.targetPort = mainServerPort;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
            if (addr != null) {
                clientIp = addr.getAddress().getHostAddress();
                if ("0:0:0:0:0:0:0:1".equals(clientIp) || "::1".equals(clientIp)) {
                    clientIp = "127.0.0.1";
                }
                ctx.channel().attr(AttributeKeys.CLIENT_IP).set(clientIp);
            }
            super.channelActive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (ServerManager.getInstance().getProxyConfig().isEnableProxyProtocol() && msg instanceof HAProxyMessage) {
                HAProxyMessage proxyMsg = (HAProxyMessage) msg;
                if (proxyMsg.command() != null && proxyMsg.command().name().equals("PROXY")) {
                    InetSocketAddress remoteAddr = (InetSocketAddress) ctx.channel().remoteAddress();
                    String remoteIp = remoteAddr != null ? remoteAddr.getAddress().getHostAddress() : null;
                    boolean trusted = ServerManager.getInstance().getProxyConfig().getTrustedProxies().contains(remoteIp);
                    if (trusted) {
                        String realIp = proxyMsg.sourceAddress();
                        if (realIp != null && !realIp.isEmpty()) {
                            clientIp = realIp;
                            ctx.channel().attr(AttributeKeys.CLIENT_IP).set(clientIp);
                        }
                    } else {
                        SubBridgeMod.LOGGER.warn("[SubBridge] rejected PROXY header from untrusted source: {}", remoteIp);
                    }
                }
                proxyMsg.release();
                return;
            }

            if (state == State.FORWARD && backendConnected && backendChannel != null && backendChannel.isActive()) {
                backendChannel.writeAndFlush(((ByteBuf) msg).retain());
                return;
            }

            if (!(msg instanceof ByteBuf)) {
                ctx.fireChannelRead(msg);
                return;
            }

            ByteBuf buf = (ByteBuf) msg;
            pendingBuffer.writeBytes(buf);

            while (!decided && pendingBuffer.readableBytes() > 0) {
                ByteBuf parseBuf = pendingBuffer.duplicate();
                parseBuf.markReaderIndex();

                try {
                    if (parseBuf.readableBytes() < 1) break;
                    parseBuf.markReaderIndex();
                    int length = readVarInt(parseBuf);
                    if (length < 0) {
                        parseBuf.resetReaderIndex();
                        break;
                    }

                    int totalLen = parseBuf.readerIndex() + length;
                    if (pendingBuffer.readableBytes() < totalLen) {
                        break;
                    }

                    byte[] rawPacket = new byte[totalLen];
                    pendingBuffer.getBytes(0, rawPacket);

                    ByteBuf payloadBuf = pendingBuffer.slice(parseBuf.readerIndex(), length);
                    FriendlyByteBuf pbuf = new FriendlyByteBuf(payloadBuf.duplicate());
                    int packetId = pbuf.readVarInt();

                    if (state == State.HANDSHAKE) {
                        if (packetId == 0x00) {
                            int protocolVersion = pbuf.readVarInt();
                            String serverHost = pbuf.readUtf(255);
                            int serverPort = pbuf.readUnsignedShort();
                            int nextState = pbuf.readVarInt();

                            if (nextState == 1) {
                                state = State.STATUS;
                                targetPort = mainServerPort;
                                packetList.add(rawPacket);
                                decided = true;
                                pendingBuffer.readerIndex(totalLen);
                                pendingBuffer.discardReadBytes();
                                break;
                            } else if (nextState == 2 || nextState == 3) {
                                state = State.LOGIN;
                                packetList.add(rawPacket);
                                pendingBuffer.readerIndex(totalLen);
                                pendingBuffer.discardReadBytes();
                                continue;
                            } else {
                                targetPort = mainServerPort;
                                packetList.add(rawPacket);
                                decided = true;
                                pendingBuffer.readerIndex(totalLen);
                                pendingBuffer.discardReadBytes();
                                break;
                            }
                        } else {
                            targetPort = mainServerPort;
                            packetList.add(rawPacket);
                            decided = true;
                            pendingBuffer.readerIndex(totalLen);
                            pendingBuffer.discardReadBytes();
                            break;
                        }
                    } else if (state == State.LOGIN) {
                        if (packetId == 0x00) {
                            String username = pbuf.readUtf(16);
                            UUID uuid = null;
                            if (payloadBuf.readableBytes() >= 16) {
                                try {
                                    uuid = pbuf.readUUID();
                                } catch (Exception e) {
                                    // ignore
                                }
                            }

                            String clientIp = ctx.channel().attr(AttributeKeys.CLIENT_IP).get();
                            String routeMode = ServerManager.getInstance().getProxyConfig().getRouteMode();
                            boolean hit = false;

                            // 检查 UUID 缓存
                            if (uuid != null) {
                                TransferCache.TransferRequest req = TransferCache.peek(uuid);
                                if (req != null && ServerManager.getInstance().isRunning(req.target.getName())) {
                                    boolean ipMatch = true;
                                    if (routeMode.equals("strict_ip") || routeMode.equals("ip_mode")) {
                                        if (clientIp != null && !clientIp.equals("127.0.0.1")) {
                                            TransferCache.TransferRequest ipReq = TransferCache.peekByIp(clientIp);
                                            if (ipReq == null || ipReq.target != req.target) {
                                                ipMatch = false;
                                            }
                                        }
                                    }
                                    if (ipMatch) {
                                        targetPort = req.target.getPort();
                                        hit = true;
                                    }
                                }
                            }

                            // 检查用户名缓存（如果尚未命中）
                            if (!hit && username != null) {
                                TransferCache.TransferRequest req = TransferCache.peekByUsername(username);
                                if (req != null && ServerManager.getInstance().isRunning(req.target.getName())) {
                                    boolean ipMatch = true;
                                    if (routeMode.equals("strict_ip") || routeMode.equals("ip_mode")) {
                                        if (clientIp != null && !clientIp.equals("127.0.0.1")) {
                                            TransferCache.TransferRequest ipReq = TransferCache.peekByIp(clientIp);
                                            if (ipReq == null || ipReq.target != req.target) {
                                                ipMatch = false;
                                            }
                                        }
                                    }
                                    if (ipMatch) {
                                        targetPort = req.target.getPort();
                                        hit = true;
                                    }
                                }
                            }

                            // 检查 IP 缓存（如果尚未命中且开启 IP 路由）
                            if (!hit && ServerManager.getInstance().getProxyConfig().isEnableIpRouting() && clientIp != null) {
                                TransferCache.TransferRequest req = TransferCache.peekByIp(clientIp);
                                if (req != null && ServerManager.getInstance().isRunning(req.target.getName())) {
                                    targetPort = req.target.getPort();
                                    hit = true;
                                }
                            }

                            // ★★★ 关键修复：一旦命中，清除所有相关缓存 ★★★
                            if (hit) {
                                if (uuid != null) TransferCache.getAndRemove(uuid);
                                if (username != null) TransferCache.getAndRemoveByUsername(username);
                                if (clientIp != null) TransferCache.getAndRemoveByIp(clientIp);
                            } else {
                                // 未命中则路由到主服
                                targetPort = mainServerPort;
                            }

                            packetList.add(rawPacket);
                            decided = true;
                            pendingBuffer.readerIndex(totalLen);
                            pendingBuffer.discardReadBytes();
                            break;
                        } else {
                            targetPort = mainServerPort;
                            packetList.add(rawPacket);
                            decided = true;
                            pendingBuffer.readerIndex(totalLen);
                            pendingBuffer.discardReadBytes();
                            break;
                        }
                    } else {
                        targetPort = mainServerPort;
                        packetList.add(rawPacket);
                        decided = true;
                        pendingBuffer.readerIndex(totalLen);
                        pendingBuffer.discardReadBytes();
                        break;
                    }

                } catch (Exception e) {
                    break;
                }
            }

            if (decided) {
                connectBackend(ctx, targetPort);
                state = State.FORWARD;
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

        private void connectBackend(ChannelHandlerContext ctx, int port) {
            Bootstrap b = new Bootstrap()
                    .group(ctx.channel().eventLoop())
                    .channel(ctx.channel().getClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // no additional handlers needed
                        }
                    });

            b.connect("127.0.0.1", port).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    backendChannel = future.channel();
                    backendConnected = true;

                    String realIp = ctx.channel().attr(AttributeKeys.CLIENT_IP).get();
                    if (realIp != null && !realIp.equals("127.0.0.1")) {
                        ByteBuf ipPacket = Unpooled.buffer();
                        ipPacket.writeInt(0xCAFEBABE);
                        byte[] ipBytes = realIp.getBytes(StandardCharsets.UTF_8);
                        writeVarInt(ipPacket, ipBytes.length);
                        ipPacket.writeBytes(ipBytes);
                        backendChannel.writeAndFlush(ipPacket);
                    }

                    for (byte[] pkt : packetList) {
                        backendChannel.writeAndFlush(Unpooled.copiedBuffer(pkt));
                    }
                    packetList.clear();

                    if (pendingBuffer != null && pendingBuffer.readableBytes() > 0) {
                        backendChannel.writeAndFlush(pendingBuffer.retain());
                        pendingBuffer.release();
                        pendingBuffer = null;
                    }

                    bindChannels(ctx.channel(), backendChannel);

                } else {
                    if (pendingBuffer != null) {
                        pendingBuffer.release();
                        pendingBuffer = null;
                    }
                    packetList.clear();
                    ctx.close();
                }
            });
        }

        private void writeVarInt(ByteBuf buf, int value) {
            do {
                byte b = (byte) (value & 0x7F);
                value >>>= 7;
                if (value != 0) b |= 0x80;
                buf.writeByte(b);
            } while (value != 0);
        }

        private void bindChannels(Channel frontend, Channel backend) {
            backend.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    if (frontend.isActive()) {
                        frontend.writeAndFlush(msg);
                    } else {
                        ctx.close();
                    }
                }

                @Override
                public void channelInactive(ChannelHandlerContext ctx) {
                    if (frontend.isActive()) {
                        frontend.close();
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    ctx.close();
                }
            });

            frontend.closeFuture().addListener(f -> {
                if (backend.isActive()) {
                    backend.close();
                }
            });
            backend.closeFuture().addListener(f -> {
                if (frontend.isActive()) {
                    frontend.close();
                }
            });
            frontend.config().setAutoRead(true);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (pendingBuffer != null) {
                pendingBuffer.release();
                pendingBuffer = null;
            }
            packetList.clear();
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (pendingBuffer != null) {
                pendingBuffer.release();
                pendingBuffer = null;
            }
            packetList.clear();
            super.channelInactive(ctx);
        }
    }
}