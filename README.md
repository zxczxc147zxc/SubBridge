SubBridge – Fabric 跨服代理桥梁 Mod

SubBridge 是一个为 Minecraft Java Edition 26.2 开发的 Fabric 模组，它通过一个高性能的 TCP 代理 让多个子服共享同一个公网端口，并根据玩家信息（UUID、用户名、IP）智能路由到对应的后端服务器，轻松实现“统一入口”和“跨服传送”体验。

📦 项目信息
作者：zxczxc147zxc

仓库：https://github.com/zxczxc147zxc/SubBridge

许可证：MIT

环境：服务端 / 客户端（推荐在服务端使用）

🧩 背景与技术栈
Minecraft 26.2（代号 "Chaos Cubed"）于 2026年6月16日 发布，引入了硫磺洞穴、硫磺 Cube 等新内容，并初步支持 Vulkan 图形 API。此版本要求 Java SE 25。

本模组基于以下技术构建：

组件	版本
Fabric Loader	0.19.3
Fabric API	0.155.0+26.2
Gradle	9.5.1
Fabric Loom	1.17.12
Netty (HAProxy Codec)	4.2.13.Final
为什么用 Netty？ – 提供异步、非阻塞的网络通信，支持 PROXY Protocol，可无缝对接 Nginx 等前端代理。

🚀 核心功能
统一代理入口
在配置的端口（如 25566）启动代理，玩家只需连接该端口，代理根据规则分发流量。

智能路由

缓存路由：玩家使用 /sub server <name> 后，代理会缓存其 UUID、用户名 和 IP，玩家重连时自动路由至目标子服。

IP 路由：支持 ip_mode 或 strict_ip 两种模式，灵活控制路由策略。

子服生命周期管理
通过命令管理子服进程：

/sub start <name> – 启动子服

/sub stop <name> – 停止子服

/sub list – 查看所有子服状态

支持自定义启动脚本（.bat、.sh、.ps1）

动态注册外部子服
无需修改配置文件，即可临时注册或移除正在运行的外部子服：

/sub registerport <port> [name]

/sub unregister <name>

PROXY Protocol 支持
开启后可从 HAProxy、Nginx 等代理中获取真实客户端 IP，提升安全性。

配置热重载
/sub reload 可重新加载 servers.json 和 ProxyServer.txt，无需重启服务端。

⚙️ 配置文件
所有配置文件位于 config/subbridge/ 目录：

1. 代理配置文件：ProxyServer.txt
properties
# SubBridge 代理配置文件
externalAddress=127.0.0.1      # 玩家连接的公网地址

proxyPort=25566                # 代理监听端口

cacheTimeoutSeconds=1          # 缓存有效期（秒）

enableIpRouting=true           # 是否启用 IP 路由

mainServerPort=0               # 主服端口（0=自动读取 server.properties）

routeMode=ip_mode              # 路由模式：ip_mode / strict_ip

externalPort=0                 # 公网映射端口（默认同 proxyPort）

enableProxyProtocol=false      # 是否开启 PROXY Protocol

2. 子服定义文件：servers.json(注意：`startCommand` 为可选字段，优先级高于 `jar` 和 `jvmArgs`。)
```json
[
  {
    "name": "lobby",
    "port": 30001,
    "path": "./subservers/lobby",
    "jar": "server.jar",
    "jvmArgs": "-Xmx1024M",
    "startCommand": "start.bat"
  }
]
```

🛠️ 构建与运行
前置条件
JDK 25（下载）

Git

克隆并构建
```bash
git clone https://github.com/zxczxc147zxc/SubBridge.git
cd SubBridge
./gradlew build      # 在 Windows 下使用 gradlew.bat
构建产物位于 build/libs/，将生成的 JAR 放入服务端的 mods/ 文件夹即可。
```
| 命令 | 说明 | 权限 |
|------|------|------|
| `/sub server <name>` | 传送至指定子服（需要代理在线） | 玩家 |
| `/sub start <name>` | 启动子服进程 | 管理员 |
| `/sub stop <name>` | 停止子服进程 | 管理员 |
| `/sub list` | 列出所有子服及运行状态 | 任意 |
| `/sub reload` | 热重载配置文件 | 管理员 |
| `/sub registerport <port> [name]` | 注册外部子服（临时） | 管理员 |
| `/sub unregister <name>` | 移除临时子服 | 管理员 |

强烈建议配合我们另一个项目https://github.com/zxczxc147zxc/CrossChatBridge它提供了跨服聊天和显示子服的玩家
🤝 贡献
欢迎提出 Issue 和 Pull Request！
如果你发现 Bug 或想增加新功能，请先开 Issue 讨论，避免重复劳动。

📄 许可证
本项目采用 MIT 许可证，详见 LICENSE 文件。

🙏 致谢
Fabric 社区

Netty 项目

最后更新：2026年7月
