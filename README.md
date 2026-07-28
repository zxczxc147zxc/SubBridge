realip 分支 – SubBridge-RealIP（辅助模组）
SubBridge-RealIP – 专为 SubBridge 生态设计的真实 IP 透传模组，用于子服端获取玩家真实 IP。

作用：配合 SubBridge 主模组（或兼容的自定义代理），在连接建立时接收特殊数据包（0xCAFEBABE + IP 长度 + IP 字符串），并在玩家登录阶段自动将 Connection 的远程地址替换为真实 IP (让别的mod获取真实IP)。

⚠️ 不可独立使用：此模组必须与发送特殊数据包的代理配合。直接连接子服（未经过代理）的玩家将因无法通过握手验证而被踢出。

配合主模组工作：主模组已在代理端实现该数据包发送，因此配合使用可完美还原 IP。

额外功能：提供 /back 命令（当未检测到主模组时），可返回配置的主服（需在 config.json 中设置）。

配置：config/subbridge-realip/config.json 支持设置主服地址和端口。

📌 如需独立使用，请确保自定义代理或前置程序在握手阶段发送格式为 [0xCAFEBABE][VarInt长度][UTF-8 IP] 的数据包。

📘 修正后的仓库整体描述
SubBridge – 跨服代理 + 真实 IP 透传解决方案
本仓库包含两个互补分支：

main – SubBridge 主模组
基于 Netty 的高性能 TCP 代理，实现多子服统一入口、智能路由、子服生命周期管理，并可在握手阶段主动发送特殊数据包（0xCAFEBABE + 玩家真实 IP）给后端子服。

realip – SubBridge-RealIP 辅助模组
部署在子服端，用于接收主模组（或兼容代理）发送的 IP 数据包，并还原玩家真实 IP。不可独立使用，必须配合发送该数据包的代理。

🧩 工作原理简述
玩家通过 SubBridge 代理（main 分支） 连接，代理解析登录包后，将玩家真实 IP 以特殊协议头（0xCAFEBABE + VarInt 长度 + IP 字符串）插入连接流。

子服端加载 SubBridge-RealIP（realip 分支），通过 Mixin 在 Netty 管道中拦截该数据包，提取 IP 并存储在 Channel 属性中。

在玩家登录阶段，模组将 Connection 的远程地址替换为真实 IP，保证后续插件/模组能正确获取玩家来源。

📌 使用条件
模组	部署位置	是否必须
SubBridge（主模组）	代理服务器 / 主服	是（提供代理功能和 IP 数据包发送）
SubBridge-RealIP（次模组）	每个子服（后端服务器）	是（用于还原 IP）
其他代理（需自定义）	任意	可选，需实现相同数据包格式
⚠️ 重要提示
次模组不能直接安装到玩家客户端，它只用于服务端。

如果没有主模组或兼容代理发送 IP 数据包，玩家连接子服会因无法解析握手包而被断开，因此次模组必须配合对应代理使用。

如果你希望单独使用 RealIP 功能而不依赖主模组，你需要自己实现一个能发送 0xCAFEBABE 头部的代理（例如用 Nginx 的 PROXY Protocol + 自定义 LUA，但格式需要匹配）。
