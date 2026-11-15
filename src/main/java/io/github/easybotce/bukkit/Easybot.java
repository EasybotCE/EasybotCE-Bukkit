package io.github.easybotce.bukkit;

import io.github.easybotce.bukkit.event.PlayerEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;

public final class Easybot extends JavaPlugin {
    private BridgeClient client;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String token = getConfig().getString("service.token", "").trim();
        if (token.isEmpty()) {
            getLogger().warning("请填写 config.yml 中的 service.token，再重启服务器。");
            return;
        }

        URI address;
        try {
            address = URI.create(getConfig().getString("service.url", "").trim());
            if ((!"ws".equals(address.getScheme()) && !"wss".equals(address.getScheme()))
                    || address.getHost() == null || address.getUserInfo() != null
                    || address.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            getLogger().warning("service.url 必须是有效的 ws:// 或 wss:// 桥接地址。");
            return;
        }

        BridgeImpl bridge = new BridgeImpl(this);
        client = new BridgeClient(address, token, getDescription().getVersion(), getServer().getVersion(),
                getLogger(), bridge::connected, bridge::broadcast);
        bridge.useClient(client);
        getServer().getPluginManager().registerEvents(new PlayerEvents(this, bridge), this);
        client.start();
    }

    @Override
    public void onDisable() {
        if (client != null) {
            client.close();
        }
    }
}
