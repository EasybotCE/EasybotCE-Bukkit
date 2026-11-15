package io.github.easybotce.bukkit.event;

import io.github.easybotce.bukkit.BridgeImpl;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class PlayerEvents implements Listener {
    private final BridgeImpl bridge;

    public PlayerEvents(BridgeImpl bridge) {
        this.bridge = bridge;
    }

    public void register(JavaPlugin plugin) {
        PluginManager manager = plugin.getServer().getPluginManager();
        manager.registerEvents(this, plugin);
        ClassLoader serverLoader = Event.class.getClassLoader();
        try {
            Class<? extends Event> eventType = Class.forName("io.papermc.paper.event.player.AsyncChatEvent",
                    false, serverLoader).asSubclass(Event.class);
            Class<?> serializerType = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer",
                    false, serverLoader);
            Method message = eventType.getMethod("message");
            Method serialize = serializerType.getMethod("serialize", message.getReturnType());
            Object serializer = serializerType.getMethod("plainText").invoke(null);
            manager.registerEvent(eventType, this, EventPriority.MONITOR, (listener, event) -> {
                try {
                    String text = (String) serialize.invoke(serializer, message.invoke(event));
                    bridge.chat(((PlayerEvent) event).getPlayer(), text);
                } catch (ReflectiveOperationException error) {
                    throw new EventException(error);
                }
            }, plugin, true);
        } catch (ClassNotFoundException ignored) {
            manager.registerEvent(AsyncPlayerChatEvent.class, this, EventPriority.MONITOR, (listener, event) -> {
                AsyncPlayerChatEvent chat = (AsyncPlayerChatEvent) event;
                bridge.chat(chat.getPlayer(), chat.getMessage());
            }, plugin, true);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("无法注册 Paper 聊天事件。", error);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        bridge.playerJoined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        bridge.playerQuit(event.getPlayer());
    }
}
