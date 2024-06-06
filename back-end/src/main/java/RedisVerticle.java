import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RedisVerticle extends AbstractVerticle {
    private Redis redis;
    private final Map<String, Set<String>> roomSubscriptions = new HashMap<>();
    private final Map<String, RedisConnection> redisConnections = new HashMap<>();
    private final Map<String, Set<String>> wsSubscriptions = new HashMap<>();

    @Override
    public void start() {
        RedisOptions options = new RedisOptions()
            .setConnectionString("redis://localhost:6379")
            .setMaxPoolSize(128)
            .setMaxWaitingHandlers(512);
        redis = Redis.createClient(vertx, options);

        vertx.eventBus().consumer("redis.action", message -> {
            JsonObject json = (JsonObject) message.body();
            String action = json.getString("action");
            String room = json.getString("room");
            String wsId = json.getString("wsId");

            switch (action) {
                case "subscribe":
                    handleSubscribe(room, wsId);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(wsId);
                    break;
                case "publish":
                    handlePublish(room, json.getString("message"));
                    break;
                default:
                    System.err.println("Unknown action: " + action);
            }
        });
    }

    private void handleSubscribe(String room, String wsId) {
        roomSubscriptions.putIfAbsent(room, new HashSet<>());
        roomSubscriptions.get(room).add(wsId);

        wsSubscriptions.putIfAbsent(wsId, new HashSet<>());
        wsSubscriptions.get(wsId).add(room);

        if (!redisConnections.containsKey(room)) {
            redis.connect(onConnect -> {
                if (onConnect.succeeded()) {
                    RedisConnection connection = onConnect.result();
                    connection.handler(message -> {
                        if (message.size() > 0 && "message".equals(message.get(0).toString())) {
                            String receivedMessage = message.get(2).toString();
                            JsonObject receivedJson = new JsonObject(receivedMessage);
                            String receivedRoom = receivedJson.getString("room");
                            vertx.eventBus().publish("room." + receivedRoom, receivedMessage);
                        }
                    });

                    connection.send(Request.cmd(Command.SUBSCRIBE).arg(room), res -> {
                        if (res.succeeded()) {
                            redisConnections.put(room, connection);
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
    }

    private void handleUnsubscribe(String wsId) {
        System.out.println("Unsubscribing wsId: " + wsId);
        Set<String> rooms = wsSubscriptions.remove(wsId);
        if (rooms != null) {
            for (String room : rooms) {
                Set<String> wsSet = roomSubscriptions.get(room);
                if (wsSet != null) {
                    wsSet.remove(wsId);
                    if (wsSet.isEmpty()) {
                        roomSubscriptions.remove(room);
                        RedisConnection connection = redisConnections.remove(room);
                        if (connection != null) {
                            connection.send(Request.cmd(Command.UNSUBSCRIBE).arg(room), res -> {
                                if (res.succeeded()) {
                                    connection.close();
                                    System.out.println("Unsubscribed from Redis channel: " + room);
                                } else {
                                    System.err.println("Failed to unsubscribe from Redis channel: " + res.cause().getMessage());
                                }
                            });
                        }
                    }
                }
            }
        } else {
            System.out.println("No rooms found for wsId: " + wsId);
        }
    }

    private void handlePublish(String room, String message) {
        redis.send(Request.cmd(Command.PUBLISH).arg(room).arg(message)).onSuccess(conn -> {
            System.out.println("Message published to Redis channel: " + room);
        }).onFailure(err -> {
            System.err.println("Failed to publish message to Redis channel: " + room);
            err.printStackTrace();
        });
    }
}
