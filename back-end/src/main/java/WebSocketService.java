import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class WebSocketService extends AbstractVerticle {
    private final Map<String, ServerWebSocket> webSocketMap = new HashMap<>();

    @Override
    public void start() {
        HttpServer server = vertx.createHttpServer();
        server.webSocketHandler(this::handleWebSocket).listen(8090);
    }

    private void handleWebSocket(ServerWebSocket ws) {
        System.out.println("WebSocket connection opened");

        ws.handler(buffer -> {
            String message = buffer.toString();
            System.out.println("Received message: " + message);

            JsonObject json = new JsonObject(message);
            JsonObject data = json.getJsonObject("data");
            String event = json.getString("event");
            switch (event) {
                case "join":
                    handleJoin(ws, data);
                    break;
                case "message":
                    handleMessage(data, ws);
                    break;
                default:
                    System.err.println("Unknown event: " + event);
            }
        });

        ws.closeHandler(close -> {
            System.out.println("WebSocket connection closed");

            String wsKey = ws.headers().get("Sec-WebSocket-Key");
            if (wsKey != null) {
                if (webSocketMap.containsKey(wsKey)) {
                    webSocketMap.remove(wsKey);

                    // Notify RedisVerticle to unsubscribe
                    JsonObject closeMessage = new JsonObject()
                        .put("action", "unsubscribe")
                        .put("wsId", wsKey);
                    vertx.eventBus().send("redis.action", closeMessage);
                    System.out.println("Sent unsubscribe message for WebSocket ID: " + wsKey);
                } else {
                    System.out.println("WebSocket key not found in map: " + wsKey);
                }
            } else {
                System.err.println("Sec-WebSocket-Key header is null");
            }
        });

        ws.exceptionHandler(exception -> {
            System.err.println("WebSocket error: " + exception.getMessage());
        });
    }

    private void handleJoin(ServerWebSocket ws, JsonObject data) {
        String wsKey = ws.headers().get("Sec-WebSocket-Key");
        if (wsKey != null) {
            String user = data.getString("user");
            String room = data.getString("room");

            ServerWebSocket existingSocket = webSocketMap.get(wsKey);
            if (existingSocket != null && !existingSocket.equals(ws)) {
                existingSocket.close();
            }

            webSocketMap.put(wsKey, ws);
            System.out.println("User " + user + " joined room: " + room);

            // Notify RedisVerticle to subscribe to the room
            JsonObject joinMessage = new JsonObject()
                .put("action", "subscribe")
                .put("room", room)
                .put("user", user)
                .put("wsId", wsKey);
            vertx.eventBus().send("redis.action", joinMessage);
            System.out.println("Sent subscribe message for room: " + room + " and user: " + user);

            // Message handler from Redis
            vertx.eventBus().consumer("room." + room, message -> {
                if (ws.equals(webSocketMap.get(wsKey))) {
                    ws.writeTextMessage((String) message.body());
                }
            });
        } else {
            System.err.println("Sec-WebSocket-Key header is null");
        }
    }

    private void handleMessage(JsonObject data, ServerWebSocket ws) {
        String room = data.getString("room");
        String message = data.encode();

        // Forward message to RedisVerticle for publishing
        JsonObject publishMessage = new JsonObject()
            .put("action", "publish")
            .put("room", room)
            .put("message", message);
        vertx.eventBus().send("redis.action", publishMessage);
        System.out.println("Sent publish message for room: " + room);
    }
}
