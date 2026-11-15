package io.github.easybotce.bukkit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Exercises the client over a real WebSocket using the Core packet format. */
public final class BridgeCheck {
    public static void main(String[] args) throws Exception {
        CompletableFuture<Integer> port = new CompletableFuture<>();
        LinkedBlockingQueue<WebSocket> identified = new LinkedBlockingQueue<>();
        CompletableFuture<JsonObject> chatFromGame = new CompletableFuture<>();
        CompletableFuture<String> chatFromGroup = new CompletableFuture<>();
        CompletableFuture<String> players = new CompletableFuture<>();
        WebSocketServer server = new WebSocketServer(new InetSocketAddress("127.0.0.1", 0), 1) {
            @Override
            public void onStart() {
                port.complete(getPort());
            }

            @Override
            public void onOpen(WebSocket socket, ClientHandshake handshake) {
                socket.send("{\"op\":0,\"interval\":1}");
            }

            @Override
            public void onClose(WebSocket socket, int code, String reason, boolean remote) {
            }

            @Override
            public void onError(WebSocket socket, Exception error) {
                port.completeExceptionally(error);
                chatFromGame.completeExceptionally(error);
            }

            @Override
            public void onMessage(WebSocket socket, String text) {
                JsonObject frame = JsonParser.parseString(text).getAsJsonObject();
                int op = frame.get("op").getAsInt();
                if (op == 1 && "test-token".equals(frame.get("token").getAsString())) {
                    socket.send("{\"op\":3}");
                } else if (op == 2) {
                    identified.add(socket);
                } else if (op == 4) {
                    String type = frame.get("exec_op").getAsString();
                    if ("SYNC_MESSAGE".equals(type)) {
                        chatFromGame.complete(frame);
                    } else if ("SERVER_STATE_CHANGED".equals(type)) {
                        players.complete(frame.get("players").getAsString());
                    }
                }
            }
        };
        server.setDaemon(true);
        server.start();
        try (BridgeClient client = new BridgeClient(URI.create("ws://127.0.0.1:" + port.get(15, TimeUnit.SECONDS) + "/bridge"),
                "test-token", "test", "test server", Logger.getLogger("BridgeCheck"),
                () -> {}, chatFromGroup::complete)) {
            client.start();
            WebSocket first = identified.poll(15, TimeUnit.SECONDS);
            require(first != null, "missing authentication or heartbeat");
            client.sendPlayers("Alex");
            client.sendChat("Alex", "c08f378c-1cb8-4084-8a93-2a135d4884f7", "Alex", "游戏消息");
            require("Alex".equals(players.get(5, TimeUnit.SECONDS)), "player list differs");
            JsonObject gameChat = chatFromGame.get(5, TimeUnit.SECONDS);
            require("游戏消息".equals(gameChat.get("message").getAsString()), "outbound chat differs");
            require("Alex".equals(gameChat.getAsJsonObject("player").get("player_name").getAsString()),
                    "outbound player differs");
            first.send("{\"op\":4,\"exec_op\":\"SEND_TO_CHAT\",\"text\":\"群消息\"}");
            require("群消息".equals(chatFromGroup.get(5, TimeUnit.SECONDS)), "inbound chat differs");
            first.close();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (System.nanoTime() < deadline) {
                WebSocket next = identified.poll(1, TimeUnit.SECONDS);
                if (next != null && next != first) {
                    return;
                }
            }
            throw new AssertionError("client did not reconnect");
        } finally {
            server.stop(1000);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
