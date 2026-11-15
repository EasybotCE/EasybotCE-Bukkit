package io.github.easybotce.bukkit;

import io.github.easybotce.bukkit.event.PlayerEvents;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class Easybot extends JavaPlugin {
    private ServerTasks tasks;
    private BridgeImpl bridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tasks = new ServerTasks(this);
        bridge = new BridgeImpl(this, tasks);
        new PlayerEvents(bridge).register(this);
        String error = connectFromConfig();
        if (error != null) {
            getLogger().warning(error + " 修改后可运行 /ebce reload。");
        }
    }

    private String connectFromConfig() {
        String token = getConfig().getString("service.token", "").trim();
        if (token.isEmpty()) {
            return "请填写 config.yml 中的 service.token。";
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
            return "service.url 必须是有效的 ws:// 或 wss:// 桥接地址。";
        }
        bridge.connect(address, token);
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("easybotcesay".equals(command.getName())) {
            String text = String.join(" ", args).trim();
            if (text.isEmpty()) {
                return false;
            }
            if (!bridge.sendCommandChat(sender, text)) {
                sender.sendMessage("桥接未连接，请用 /ebce status 查看状态。");
            }
            return true;
        }
        if (args.length == 0 || (args.length == 1 && "status".equalsIgnoreCase(args[0]))) {
            sender.sendMessage(bridge.status());
            return true;
        }
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("easybotce.reload")) {
                sender.sendMessage("你没有重载 EasyBotCE 配置的权限。");
                return true;
            }
            tasks.global(() -> {
                reloadConfig();
                String error = connectFromConfig();
                String reply = error == null ? "配置已重载，正在连接 EasyBotCE。" : "重载失败。" + error;
                if (tasks.isFolia() && sender instanceof Player) {
                    tasks.player((Player) sender, () -> sender.sendMessage(reply));
                } else {
                    sender.sendMessage(reply);
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!"easybotce".equals(command.getName()) || args.length != 1) {
            return Collections.emptyList();
        }
        List<String> options = sender.hasPermission("easybotce.reload")
                ? Arrays.asList("status", "reload") : Collections.singletonList("status");
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).collect(Collectors.toList());
    }

    @Override
    public void onDisable() {
        if (bridge != null) {
            bridge.disconnect();
        }
    }
}
