# EasyBotCE Bukkit

EasyBotCE 的 Minecraft 服务端插件。通过 WebSocket 接入 [EasybotCE Core](https://github.com/EasybotCE/Core)，按 Core 中的同步规则在游戏和 OneBot 11 群之间转发文字消息。

- 玩家可以直接聊天，也可以用 `/ebcesay` 手动发送。
- 插件上报在线玩家列表，Core 管理台显示连接状态和人数。
- 断线后自动重连。断线期间的聊天消息会跳过。

## 兼容性

| 服务端 | 验证情况 |
| --- | --- |
| Paper 26.2 / Java 25 | 已验证加载、桥接连接、指令和配置重载 |
| Folia 1.21.11 / Java 21 | 已验证玩家进退、双向桥接聊天、指令和配置重载 |
| 其他 Bukkit、Spigot、Paper 或 Folia 版本 | 使用 Spigot API 1.13.2 编译，声明 `api-version: '1.13'`，尚未逐版本验收 |

插件产物使用 Java 8 字节码。运行时使用游戏服务端要求的 Java 版本。支持 `AsyncChatEvent` 的 Paper 和 Folia 使用 Adventure 读取纯文本，旧版 Bukkit 走 `AsyncPlayerChatEvent`。Folia 上的全局任务与玩家操作分别使用全局调度器和实体调度器。

## 安装与接入

1. **在 Core 添加服务器。** 打开 Core 管理台，在 **接入 → Minecraft 服务器** 新建并启用服务器记录，再打开该记录复制桥接令牌。
2. **安装并配置插件。** 将 [构建得到的 JAR](#从源码构建) 放入游戏服务端的 `plugins/`，启动一次生成 `plugins/EasyBotCE/config.yml`。填入下面两项，然后在控制台运行 `ebce reload`，用 `ebce status` 检查连接。

   ```yaml
   service:
     url: "ws://127.0.0.1:26991/bridge"
     token: "填入 Core 为此服务器生成的桥接令牌"
   ```

   同机部署可使用默认地址。异机部署时，按 Core 启动日志确认桥接地址，调整 Core 的 `ServerOptions.Host` 和 `ServerOptions.Port`，并把插件指向可达的地址。跨公网连接使用 `wss://`。桥接令牌仅保存在运行配置中。

3. **配置群聊同步。** 在 Core 中配置并启动 OneBot 11 正向 WebSocket 适配器。进入 **消息 → 同步规则**，选择对应的服务器和适配器，填写实际群号，选择 **双向同步** 并启用规则。从普通群成员账号和游戏内各发一条消息验证。

## 指令

游戏内使用斜杠，服务器控制台可直接输入命令。配置过的同步规则决定消息的目标、方向和格式。

| 指令 | 简写 | 用途 |
| --- | --- | --- |
| `/easybotce status` | `/ebce status` | 查看插件与 Core 的桥接状态 |
| `/easybotce reload` | `/ebce reload` | 重读插件配置并重新连接 Core。需要 `easybotce.reload`，默认授予 OP |
| `/easybotcesay <消息>` | `/ebcesay <消息>` | 按同步规则发送文字消息 |

## 从源码构建

使用 JDK 17 或 21，在仓库根目录通过 Gradle Wrapper 构建。

```bash
./gradlew build
```

Windows 使用 `.\gradlew.bat build`。构建会运行桥接自检，并生成包含网络和 JSON 依赖的 `build/libs/EasyBotCE-Bukkit-0.1.0.jar`。

- [插件入口与桥接实现](src/main/java/io/github/easybotce/bukkit)
- [默认配置](src/main/resources/config.yml)
- [桥接自检](src/test/java/io/github/easybotce/bukkit/BridgeCheck.java)

## 参考

项目布局和服务端接入流程参考了原版 EasyBot Bukkit。当前同步范围是纯文本与在线玩家列表。账号绑定和第三方聊天插件的专用接入仍待实现。 [原版插件仓库](https://github.com/easybot-team/easybot-bukkit) · [原版安装文档](https://docs.inectar.cn/docs/easybot/quick_start/plugin/bukkit/install_bukkit/)
