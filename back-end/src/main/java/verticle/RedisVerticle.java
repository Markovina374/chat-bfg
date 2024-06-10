package verticle;

import helper.PasswordHelper;
import helper.RedisActionEvent;
import helper.RedisAuthAction;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
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

import static helper.ConstantHolder.ACTION;
import static helper.ConstantHolder.ERROR;
import static helper.ConstantHolder.LOGIN;
import static helper.ConstantHolder.MESSAGE;
import static helper.ConstantHolder.MESSAGES;
import static helper.ConstantHolder.OK;
import static helper.ConstantHolder.PASSWORD;
import static helper.ConstantHolder.REDIS_ACTION;
import static helper.ConstantHolder.REDIS_AUTH;
import static helper.ConstantHolder.ROOM;
import static helper.ConstantHolder.STATUS;
import static helper.ConstantHolder.WS_ID;

/**
 * Вертикл отвечающий за работу с Редисом
 */
public class RedisVerticle extends AbstractVerticle {
    private Redis redis;

    /**
     * Эти 3 мапы служат для того, что бы реализвать корректную подписку на канал сообщений и последующее закрытие конекшена к базе
     */
    private final Map<String, Set<String>> roomSubscriptions = new HashMap<>();
    private final Map<String, RedisConnection> redisConnections = new HashMap<>();
    private final Map<String, Set<String>> wsSubscriptions = new HashMap<>();

    /**
     * Стандартный метод инициализации
     */
    @Override
    public void start() {
        RedisOptions options = new RedisOptions()
            .setConnectionString("redis://localhost:6379") //todo вынести в файлик конфигурации
            .setMaxPoolSize(128)
            .setMaxWaitingHandlers(512);
        redis = Redis.createClient(vertx, options);

        vertx.eventBus().consumer(REDIS_AUTH, message -> {
            JsonObject json = (JsonObject) message.body();
            switch (RedisAuthAction.fromString(json.getString(ACTION))) {
                case REGISTER -> handleRegister(message, json);
                case AUTHENTICATE -> handleAuthenticate(message, json);
                default -> message.fail(1, "Unknown action");
            }
        });

        vertx.eventBus().consumer(REDIS_ACTION, message -> {
            JsonObject json = (JsonObject) message.body();
            String room = json.getString(ROOM);
            String wsId = json.getString(WS_ID);

            switch (RedisActionEvent.fromString(json.getString(ACTION))) {
                case SUBSCRIBE -> handleSubscribe(room, wsId);
                case UNSUBSCRIBE -> handleUnsubscribe(wsId);
                case PUBLISH -> handlePublish(room, json.getString(MESSAGE));
                case GET_MESSAGES -> getMessagesFromRoom(message, room);
                default -> message.fail(1, "Unknown action");
            }
        });
    }

    /**
     * Метод для регистрации новых пользователей, перед регистрацией метод проверяет есть ли уже Юзер с таким логином
     *
     * @param message сообщение, по которому отправляюбтся данные назад
     * @param data    креды пользователя
     */
    private void handleRegister(Message<Object> message, JsonObject data) {
        String login = data.getString(LOGIN);
        String password = data.getString(PASSWORD);

        String userKey = "user:" + login;

        redis.send(Request.cmd(Command.HEXISTS).arg(userKey).arg(PASSWORD)).onSuccess(exists -> {
            if (exists.toInteger() == 0) {
                redis.send(Request.cmd(Command.HSET)
                    .arg(userKey)
                    .arg(PASSWORD)
                    .arg(password)).onSuccess(res -> {
                    message.reply(new JsonObject().put(STATUS, OK));
                }).onFailure(err -> {
                    message.reply(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Registration failed"));
                });
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "User already exists"));
            }
        }).onFailure(err -> {
            message.reply(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Failed to check user existence"));
        });
    }

    /**
     * Метод для проверки пароля пользователя для Аутентификации
     *
     * @param message сообщение по которому можно отправить обратный ответ
     * @param data    креды пользователя
     */
    private void handleAuthenticate(Message<Object> message, JsonObject data) {
        String login = data.getString(LOGIN);
        String password = data.getString(PASSWORD);

        redis.send(Request.cmd(Command.HGET)
            .arg("user:" + login).arg(PASSWORD)).onSuccess(res -> {
            if (res != null && PasswordHelper.isValid(res.toBytes(), password)) {
                message.reply(new JsonObject().put(STATUS, OK));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Invalid credentials"));
            }
        }).onFailure(err -> {
            message.reply(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Authentication failed"));
        });
    }

    /**
     * Метод подписки на канал
     *
     * @param room номер комнаты
     * @param wsId идентификатор Вебсокета
     */
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
                        if (message.size() > 0 && MESSAGE.equals(message.get(0).toString())) {
                            String receivedMessage = message.get(2).toString();
                            JsonObject receivedJson = new JsonObject(receivedMessage);
                            String receivedRoom = receivedJson.getString(ROOM);
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

    /**
     * Метод отписки от канала, так как один пользователь может быть подписан на канал, происходит массовая отписка от всех каналов
     *
     * @param wsId идентификатор веб-сокета
     */

    private void handleUnsubscribe(String wsId) {
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

    /**
     * Метод публикации сообщений, не только в канал, но и в обычное множество, для офлайн сообщений
     *
     * @param room    идентификатор комнаты\канала
     * @param message сообщение
     */

    private void handlePublish(String room, String message) {
        redis.send(Request.cmd(Command.PUBLISH)
            .arg(room)
            .arg(message)).onSuccess(conn -> {
        }).onFailure(Throwable::printStackTrace);
        redis.send(Request.cmd(Command.RPUSH)
            .arg(room)
            .arg(message)).onSuccess(conn -> {
        }).onFailure(Throwable::printStackTrace);
    }


    /**
     * Метод для получения всех сообщений
     *
     * @param message сообщение по которому можно вернуть ответ
     * @param room    идентификатор комнаты\канала
     */
    public void getMessagesFromRoom(Message<Object> message, String room) {
        redis.send(Request.cmd(Command.LRANGE)
            .arg(room)
            .arg("0")
            .arg("-1")).onSuccess(res -> {
            JsonArray messages = new JsonArray(res.stream()
                .map(x -> new JsonObject(x.toString()))
                .toList());
            message.reply(new JsonObject().put(STATUS, OK).put(MESSAGES, messages));
        }).onFailure(err -> {
            err.printStackTrace();
        });
    }
}
