package com.zxczxc147zxc.subbridge.config;

public class ProxyConfig {
    private String externalAddress = "127.0.0.1";
    private int proxyPort = 25566;
    private int cacheTimeoutSeconds = 1;
    private boolean enableIpRouting = true;
    private int mainServerPort = 0;
    private String routeMode = "ip_mode";
    private int externalPort = 0;
    private boolean enableProxyProtocol = false; // 新增

    public String getExternalAddress() { return externalAddress; }
    public void setExternalAddress(String externalAddress) { this.externalAddress = externalAddress; }
    public int getProxyPort() { return proxyPort; }
    public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }
    public int getCacheTimeoutSeconds() { return cacheTimeoutSeconds; }
    public void setCacheTimeoutSeconds(int cacheTimeoutSeconds) { this.cacheTimeoutSeconds = cacheTimeoutSeconds; }
    public boolean isEnableIpRouting() { return enableIpRouting; }
    public void setEnableIpRouting(boolean enableIpRouting) { this.enableIpRouting = enableIpRouting; }
    public int getMainServerPort() { return mainServerPort; }
    public void setMainServerPort(int mainServerPort) { this.mainServerPort = mainServerPort; }
    public String getRouteMode() { return routeMode; }
    public void setRouteMode(String routeMode) { this.routeMode = routeMode; }
    public int getExternalPort() { return externalPort > 0 ? externalPort : proxyPort; }
    public void setExternalPort(int externalPort) { this.externalPort = externalPort; }
    public boolean isEnableProxyProtocol() { return enableProxyProtocol; }
    public void setEnableProxyProtocol(boolean enableProxyProtocol) { this.enableProxyProtocol = enableProxyProtocol; }
}