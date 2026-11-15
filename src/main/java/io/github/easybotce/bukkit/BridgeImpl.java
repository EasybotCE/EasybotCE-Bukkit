package io.github.easybotce.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.stream.Collectors;

public final class BridgeImpl {
    private final JavaPlugin plugin;
    private BridgeClient client;

    BridgeImpl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void useClient(BridgeClient client) {
        this.client = client;
    }

    void connected() {
        plugin.getServer().getScheduler().runTask(plugin, this::reportPlayers);
    }

    void broadcast(String text) {
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getServer().broadcastMessage(text));
    }

    public void chat(Player player, String text) {
        client.sendChat(player.getName(), player.getUniqueId().toString(), player.getDisplayName(), text);
    }

    public void reportPlayers() {
        String names = plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName).collect(Collectors.joining(","));
        client.sendPlayers(names);
    }
}
