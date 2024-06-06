package old;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;

import java.util.HashMap;
import java.util.Map;

public class WebSocketService extends AbstractVerticle {
    private Redis redis;

    private final Map<String, ServerWebSocket> webSocketMap = new HashMap<>();

    @Override
    public void start() {
        // Настройка Redis клиента
        RedisOptions options = new RedisOptions().setConnectionString("redis://localhost:6379").setMaxPoolSize(128).setMaxWaitingHandlers(512);
        redis = Redis.createClient(vertx, options);


        HttpServer server = vertx.createHttpServer();
        server.webSocketHandler(this::handleWebSocket).listen(8090);


    }

    private void handleWebSocket(ServerWebSocket ws) {
        System.out.println("WebSocket connection opened");

        ws.handler(buffer -> {
            String message = buffer.toString();
            System.out.println("Received message: " + message);

            // Предполагаем, что сообщение в формате JSON: {"event": "join" | "message", "data": {...}}
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
            webSocketMap.values().remove(ws);
        });

        ws.exceptionHandler(exception -> {
            System.err.println("WebSocket error: " + exception.getMessage());
        });
    }

    private void handleJoin(ServerWebSocket ws, JsonObject data) {
        String user = data.getString("user");
        String room = data.getString("room");

        if (ws.equals(webSocketMap.get(getKey(user, room)))) {
            return;
        }
        webSocketMap.put(getKey(user, room), ws);
        System.out.println("здеся");

        // Подписываемся на Redis канал
        System.out.println(webSocketMap + " " + ws);
        redis.connect(onConnect -> {
            if (onConnect.succeeded()) {
                RedisConnection connection = onConnect.result();
                connection.handler(message -> {
                    System.out.println("Received message: " + message.toString());
                    if (message.size() > 0 && "message".equals(message.get(0).toString())) {
                        String receivedMessage = message.get(2).toString();
                        JsonObject receivedJson = new JsonObject(receivedMessage);
                        String receivedRoom = receivedJson.getString("room");
                        webSocketMap.forEach((key, socket) -> {
                            if (key.startsWith(receivedRoom)) {
                                socket.writeTextMessage(receivedMessage);
                            }
                        });
                    }
                });

                connection.send(Request.cmd(Command.SUBSCRIBE).arg(room), res -> {
                    if (res.succeeded()) {
                        System.out.println("Subscribed to Redis channel: " + room);
                    } else {
                        System.err.println("Failed to subscribe to Redis channel: " + res.cause().getMessage());
                    }
                });
            } else {
                System.err.println("Failed to connect to Redis: " + onConnect.cause().getMessage());
            }
        });
    }

    private String getKey(String user, String room) {
        return room + ":" + user;
    }

    private void handleMessage(JsonObject data, ServerWebSocket ws) {
        String room = data.getString("room");
        String message = data.encode();
        System.out.println("sdaasd" + webSocketMap + " " + ws);
        // Публикуем сообщение в Redis канал
        redis.send(Request.cmd(Command.PUBLISH).arg(room).arg(message))
            .onSuccess(conn -> {
                System.out.println(conn.toString());
                System.out.println("Message published to Redis channel: " + room);
            })
            .onFailure(err -> {
                System.err.println("Failed to publish message to Redis channel: " + room);
                err.printStackTrace();
                // Дополнительная логика обработки ошибок
            }).onComplete(x -> {

            });
        System.out.println("2222233");
    }
}
