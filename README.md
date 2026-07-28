SubBridge-RealIP – 专为 SubBridge 生态设计的真实 IP 透传模组，用于子服端获取玩家真实 IP。

作用：配合 SubBridge 主模组（或兼容的自定义代理），在连接建立时接收特殊数据包（0xCAFEBABE + IP 长度 + IP 字符串），并在玩家登录阶段自动将 Connection 的远程地址替换为真实 IP。

⚠️ 不可独立使用：此模组必须与发送特殊数据包的代理配合。直接连接子服（未经过代理）的玩家将因无法通过握手验证而被踢出。

配合主模组工作：主模组已在代理端实现该数据包发送，因此配合使用可完美还原 IP。

额外功能：提供 /back 命令（当未检测到主模组时），可返回配置的主服（需在 config.json 中设置）。

配置：config/subbridge-realip/config.json 支持设置主服地址和端口。

📌 如需独立使用，请确保自定义代理或前置程序在握手阶段发送格式为 [0xCAFEBABE][VarInt长度][UTF-8 IP] 的数据包。
