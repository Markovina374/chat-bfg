package verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;
import helper.PasswordHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static helper.ConstantHolder.ERROR;
import static helper.ConstantHolder.STATUS;

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

        vertx.eventBus().consumer("redis.auth", message -> {
            JsonObject json = (JsonObject) message.body();
            String action = json.getString("action");

            switch (action) {
                case "register":
                    handleRegister(message, json);
                    break;
                case "authenticate":
                    handleAuthenticate(message, json);
                    break;
                default:
                    System.err.println("Unknown action: " + action);
                    message.fail(1, "Unknown action");
            }
        });

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

    private void handleRegister(Message<Object> message, JsonObject data) {
        String login = data.getString("login");
        String password = data.getString("password");

        String userKey = "user:" + login; // Формируем ключ для хранения данных пользователя

        // Проверка, существует ли пользователь
        redis.send(Request.cmd(Command.HEXISTS).arg(userKey).arg("password")).onSuccess(exists -> {
            if (exists.toInteger() == 0) {
                // Пользователь не существует, добавляем его
                redis.send(Request.cmd(Command.HSET)
                    .arg(userKey)
                    .arg("password")
                    .arg(password)).onSuccess(res -> {
                    System.out.println("User registered: " + login);
                    message.reply(new JsonObject().put(STATUS, "ok"));
                }).onFailure(err -> {
                    System.err.println("Failed to register user: " + err.getMessage());
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "Registration failed"));
                });
            } else {
                // Пользователь уже существует
                System.out.println("User already exists: " + login);
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", "User already exists"));
            }
        }).onFailure(err -> {
            System.err.println("Failed to check user existence: " + err.getMessage());
            message.reply(new JsonObject().put(STATUS, ERROR).put("message", "Failed to check user existence"));
        });
    }

    private void handleAuthenticate(Message<Object> message, JsonObject data) {
        String login = data.getString("login");
        String password = data.getString("password");

        redis.send(Request.cmd(Command.HGET)
            .arg("user:" + login).arg("password")).onSuccess(res -> {
            if (res != null && PasswordHelper.isValid(res.toBytes(), password)) {
                message.reply(new JsonObject().put(STATUS, "ok"));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", "Invalid credentials"));
            }
        }).onFailure(err -> {
            System.err.println("Failed to authenticate user: " + err.getMessage());
            message.reply(new JsonObject().put(STATUS, ERROR).put("message", "Authentication failed"));
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
