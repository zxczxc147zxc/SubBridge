package com.zxczxc147zxc.subbridge.config;

import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private List<ServerEntry> servers = new ArrayList<>();

    public List<ServerEntry> getServers() {
        return servers;
    }

    public void setServers(List<ServerEntry> servers) {
        this.servers = servers;
    }
}