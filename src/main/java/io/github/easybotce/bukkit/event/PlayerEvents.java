package io.github.easybotce.bukkit.event;

import io.github.easybotce.bukkit.BridgeImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerEvents implements Listener {
    private final JavaPlugin plugin;
    private final BridgeImpl bridge;

    public PlayerEvents(JavaPlugin plugin, BridgeImpl bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (event.isAsynchronous()) {
            String message = event.getMessage();
            plugin.getServer().getScheduler().runTask(plugin, () -> bridge.chat(event.getPlayer(), message));
        } else {
            bridge.chat(event.getPlayer(), event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, bridge::reportPlayers);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, bridge::reportPlayers);
    }
}
