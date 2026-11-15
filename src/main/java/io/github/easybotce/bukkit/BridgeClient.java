package io.github.easybotce.bukkit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

final class BridgeClient implements AutoCloseable {
    private static final int HELLO = 0;
    private static final int IDENTIFY = 1;
    private static final int HEARTBEAT = 2;
    private static final int IDENTIFIED = 3;
    private static final int PACKET = 4;

    private final URI address;
    private final String token;
    private final String pluginVersion;
    private final String serverDescription;
    private final Logger logger;
    private final Runnable onReady;
    private final Consumer<String> onChat;
    // Session changes and WebSocket callbacks run on this worker.
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "EasyBotCE-Bridge");
        thread.setDaemon(true);
        return thread;
    });

    private Connection connection;
    private long deadline;
    private int heartbeatSeconds;
    private volatile boolean ready;

    BridgeClient(URI address, String token, String pluginVersion, String serverDescription,
                 Logger logger, Runnable onReady, Consumer<String> onChat) {
        this.address = address;
        this.token = token;
        this.pluginVersion = pluginVersion;
        this.serverDescription = serverDescription;
        this.logger = logger;
        this.onReady = onReady;
        this.onChat = onChat;
    }

    void start() {
        worker.execute(() -> {
            // Seed the provider before opening a socket, where the server applies a handshake deadline.
            new SecureRandom().nextBytes(new byte[1]);
            deadline = System.nanoTime();
            if (!worker.isShutdown()) {
                worker.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
            }
        });
    }

    private void tick() {
        long now = System.nanoTime();
        if (now - deadline < 0) {
            return;
        }
        if (connection == null) {
            Connection attempt = new Connection();
            connection = attempt;
            deadline = now + TimeUnit.SECONDS.toNanos(15);
            attempt.connect();
        } else if (!ready) {
            disconnect(connection, "等待桥接鉴权超时");
        } else {
            deadline = now + TimeUnit.SECONDS.toNanos(heartbeatSeconds);
            send(packet(HEARTBEAT));
        }
    }

    boolean isReady() {
        return ready;
    }

    void sendChat(String name, String uuid, String displayName, String text, boolean command) {
        JsonObject player = new JsonObject();
        player.addProperty("player_name", name);
        player.addProperty("player_uuid", uuid);
        player.addProperty("player_name_raw", displayName);
        JsonObject message = event("SYNC_MESSAGE");
        message.add("player", player);
        message.addProperty("message", text);
        message.addProperty("use_command", command);
        submit(() -> {
            if (ready) {
                send(message);
            }
        });
    }

    void sendPlayers(String names) {
        JsonObject message = event("SERVER_STATE_CHANGED");
        message.addProperty("players", names);
        submit(() -> {
            if (ready) {
                send(message);
            }
        });
    }

    private void receive(Connection source, String text) {
        if (connection != source) {
            return;
        }
        JsonObject message;
        int op;
        try {
            message = JsonParser.parseString(text).getAsJsonObject();
            JsonElement value = message.get("op");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException();
            }
            op = value.getAsInt();
        } catch (RuntimeException error) {
            disconnect(source, "收到无效的桥接报文");
            return;
        }
        if (!ready) {
            authenticate(source, message, op);
        } else if (op == PACKET && "SEND_TO_CHAT".equals(string(message, "exec_op"))) {
            String chat = string(message, "text");
            if (!chat.trim().isEmpty()) {
                onChat.accept(chat);
            }
        }
    }

    private void authenticate(Connection source, JsonObject message, int op) {
        if (op == HELLO && heartbeatSeconds == 0) {
            try {
                heartbeatSeconds = message.get("interval").getAsInt();
                if (heartbeatSeconds < 1 || heartbeatSeconds > 3600) {
                    throw new IllegalArgumentException();
                }
            } catch (RuntimeException error) {
                disconnect(source, "桥接心跳间隔无效");
                return;
            }
            JsonObject identify = packet(IDENTIFY);
            identify.addProperty("token", token);
            identify.addProperty("plugin_version", pluginVersion);
            identify.addProperty("server_description", serverDescription);
            send(identify);
        } else if (op == IDENTIFIED && heartbeatSeconds > 0) {
            ready = true;
            deadline = System.nanoTime();
            logger.info("已连接 EasyBotCE 桥接。");
            onReady.run();
        } else {
            disconnect(source, "桥接握手顺序无效");
        }
    }

    private void send(JsonObject frame) {
        Connection source = connection;
        try {
            source.send(frame.toString());
        } catch (RuntimeException error) {
            disconnect(source, "桥接报文发送失败");
        }
    }

    private void disconnect(Connection source, String reason) {
        if (source != connection) {
            return;
        }
        connection = null;
        ready = false;
        heartbeatSeconds = 0;
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        source.closeConnection(CloseFrame.NORMAL, "");
        logger.warning(reason + "，5 秒后重试。");
    }

    private void submit(Runnable action) {
        try {
            worker.execute(action);
        } catch (RejectedExecutionException ignored) {
            // Bukkit may disable the plugin while the socket is delivering its last event.
        }
    }

    @Override
    public void close() {
        submit(() -> {
            ready = false;
            if (connection != null) {
                connection.closeConnection(CloseFrame.NORMAL, "");
                connection = null;
            }
        });
        worker.shutdown();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static JsonObject packet(int op) {
        JsonObject packet = new JsonObject();
        packet.addProperty("op", op);
        return packet;
    }

    private static JsonObject event(String type) {
        JsonObject packet = packet(PACKET);
        packet.addProperty("exec_op", type);
        packet.addProperty("callback_id", UUID.randomUUID().toString());
        return packet;
    }

    private final class Connection extends WebSocketClient {
        Connection() {
            super(address, new Draft_6455(Collections.emptyList(), 1 << 20), Collections.emptyMap(), 5000);
            setDaemon(true);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
        }

        @Override
        public void onMessage(String text) {
            submit(() -> receive(this, text));
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            submit(() -> disconnect(this, "桥接连接已断开 (" + code + ")"));
        }

        @Override
        public void onError(Exception error) {
            submit(() -> disconnect(this, "桥接连接异常 (" + error.getClass().getSimpleName() + ")"));
        }
    }
}
