package io.github.easybotce.bukkit;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BridgeImpl {
    private final JavaPlugin plugin;
    private final ServerTasks tasks;
    private final Map<UUID, String> players = new ConcurrentHashMap<>();
    private volatile BridgeClient client;
    private volatile long generation;

    BridgeImpl(JavaPlugin plugin, ServerTasks tasks) {
        this.plugin = plugin;
        this.tasks = tasks;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            players.put(player.getUniqueId(), player.getName());
        }
    }

    void connect(URI address, String token) {
        disconnect();
        long current = generation;
        client = new BridgeClient(address, token, plugin.getDescription().getVersion(),
                plugin.getServer().getVersion(), plugin.getLogger(),
                () -> runForConnection(current, this::reportPlayers),
                text -> runForConnection(current, () -> broadcast(current, text)));
        client.start();
    }

    void disconnect() {
        generation++;
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private void runForConnection(long source, Runnable action) {
        tasks.global(() -> {
            if (source == generation) {
                action.run();
            }
        });
    }

    private void broadcast(long source, String text) {
        Server server = plugin.getServer();
        if (!tasks.isFolia()) {
            server.broadcastMessage(text);
            return;
        }
        server.getConsoleSender().sendMessage(text);
        for (Player player : server.getOnlinePlayers()) {
            tasks.player(player, () -> {
                if (source == generation && player.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
                    player.sendMessage(text);
                }
            });
        }
    }

    String status() {
        BridgeClient current = client;
        if (current == null) {
            return "桥接未启动，请检查 config.yml 中的 service.url 和 service.token。";
        }
        return current.isReady() ? "已连接 EasyBotCE 桥接。" : "桥接尚未连通，正在连接或等待重试。";
    }

    public void chat(Player player, String text) {
        tasks.player(player, () -> {
            BridgeClient current = client;
            if (current != null) {
                sendPlayerChat(current, player, text, false);
            }
        });
    }

    boolean sendCommandChat(CommandSender sender, String text) {
        BridgeClient current = client;
        if (current == null || !current.isReady()) {
            return false;
        }
        if (sender instanceof Player) {
            sendPlayerChat(current, (Player) sender, text, true);
        } else {
            current.sendChat(sender.getName(), "", sender.getName(), text, true);
        }
        return true;
    }

    private void sendPlayerChat(BridgeClient current, Player player, String text, boolean command) {
        current.sendChat(player.getName(), player.getUniqueId().toString(), player.getDisplayName(), text, command);
    }

    public void playerJoined(Player player) {
        players.put(player.getUniqueId(), player.getName());
        tasks.global(this::reportPlayers);
    }

    public void playerQuit(Player player) {
        players.remove(player.getUniqueId());
        tasks.global(this::reportPlayers);
    }

    private void reportPlayers() {
        BridgeClient current = client;
        if (current != null) {
            current.sendPlayers(String.join(",", players.values()));
        }
    }
}
