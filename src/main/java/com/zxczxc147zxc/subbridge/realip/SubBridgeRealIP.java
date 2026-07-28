package com.zxczxc147zxc.subbridge.realip;

import com.zxczxc147zxc.subbridge.realip.command.RealIPCommand;
import com.zxczxc147zxc.subbridge.realip.config.SubBridgeRealIPConfig;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubBridgeRealIP implements DedicatedServerModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("SubBridge-RealIP");

    @Override
    public void onInitializeServer() {
        LOGGER.info("SubBridge-RealIP 初始化...");

        SubBridgeRealIPConfig.getInstance();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                RealIPCommand.register(dispatcher));

        LOGGER.info("SubBridge-RealIP 初始化完成");
    }
}