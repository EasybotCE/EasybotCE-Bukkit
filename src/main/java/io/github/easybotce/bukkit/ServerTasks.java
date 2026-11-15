package io.github.easybotce.bukkit;

import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

final class ServerTasks {
    private final JavaPlugin plugin;
    private final Object globalScheduler;
    private final Method globalExecute;
    private final Method getEntityScheduler;
    private final Method entityExecute;

    ServerTasks(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!isFoliaServer()) {
            globalScheduler = null;
            globalExecute = null;
            getEntityScheduler = null;
            entityExecute = null;
            return;
        }

        // Resolve through the public interfaces so legacy Bukkit can load the same JAR.
        try {
            Method getGlobalScheduler = Server.class.getMethod("getGlobalRegionScheduler");
            globalScheduler = getGlobalScheduler.invoke(plugin.getServer());
            globalExecute = getGlobalScheduler.getReturnType().getMethod("execute", Plugin.class, Runnable.class);
            getEntityScheduler = Entity.class.getMethod("getScheduler");
            entityExecute = getEntityScheduler.getReturnType()
                    .getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("无法初始化 Folia 调度器。", error);
        }
    }

    boolean isFolia() {
        return globalScheduler != null;
    }

    void global(Runnable task) {
        if (isFolia()) {
            invoke(globalExecute, globalScheduler, plugin, task);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    void player(Player player, Runnable task) {
        if (isFolia()) {
            Object scheduler = invoke(getEntityScheduler, player);
            invoke(entityExecute, scheduler, plugin, task, null, 1L);
        } else {
            global(task);
        }
    }

    private static boolean isFoliaServer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, Server.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Folia 任务调度失败。", error);
        }
    }
}
