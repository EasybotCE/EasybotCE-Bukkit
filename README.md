# EasyBotCE Bukkit 插件

将 Minecraft 游戏聊天与 EasyBotCE Core 中配置的 OneBot 11 群双向同步。插件负责连接、鉴权、心跳、自动重连、上报玩家列表和收发聊天，群规则及格式在 Core 管理台维护。

## 构建

使用 JDK 17 或 21，在仓库根目录运行 `./gradlew build`。Windows 使用 `.\gradlew.bat build`。生成的 `build/libs/EasyBotCE-Bukkit-0.1.0.jar` 已包含桥接依赖。

Gradle 单模块、`plugin.yml`、`config.yml`、服务端桥接和 Bukkit 事件监听的布局参考 [原版 EasyBot Bukkit 仓库](https://github.com/easybot-team/easybot-bukkit)。插件使用 Java 8 字节码和 Bukkit 1.13.2 API，实际使用时按服务端版本选择 Java。

支持 `AsyncChatEvent` 的 Paper 和 Folia 使用 Adventure 纯文本同步，旧版 Bukkit API 使用 `AsyncPlayerChatEvent`。Folia 的连接重载和在线列表上报由全局调度器处理，玩家消息操作由实体调度器处理。

已在 Paper 26.2 / Java 25 和 Folia 1.21.11 / Java 21 验证。Folia 测试覆盖了玩家进退、聊天收发和桥接重载。

## 安装与接入

1. 把 JAR 放进 Bukkit、Spigot、Paper 或 Folia 的 `plugins/`，启动一次生成 `plugins/EasyBotCE/config.yml`。
2. 在配套 Core 管理台新建并启用服务器记录，编辑该记录时复制桥接令牌。把令牌填入插件配置的 `service.token`。
3. 同机运行时使用 `ws://127.0.0.1:26991/bridge`。异机运行时，修改 Core 的 `ServerOptions.Host`，然后在插件的 `service.url` 填写实际可达地址。跨公网连接使用带可信证书的 `wss://`。
4. 重启游戏服务器，确认 Core 的服务器列表显示已连接。新建 OneBot 11 适配器，填入协议端的正向 WebSocket 地址和对应令牌，启动并检查连接状态。
5. 在 Core 新建并启用一条同步规则，选择服务器和适配器，填入群号，选择双向同步。从游戏和群各发送一条文字消息，检查对端是否收到。

玩家照常聊天即可触发同步。插件会在后台重连，断线期间的消息会跳过。当前交付范围为纯文本聊天和在线玩家统计。

## 游戏指令

- `/easybotce status` 或 `/ebce status` 查看桥接连接状态。
- `/easybotce reload` 或 `/ebce reload` 重新读取插件配置并连接 Core，需要 `easybotce.reload` 权限，默认授予管理员。
- `/easybotcesay <消息>` 或 `/ebcesay <消息>` 通过 Core 中已启用的同步规则发送文字。
