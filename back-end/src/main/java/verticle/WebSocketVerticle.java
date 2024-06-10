package verticle;

import helper.AuthEvent;
import helper.RedisActionEvent;
import helper.UserStatusEvent;
import helper.WebSocketEvent;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

import static helper.ConstantHolder.ACTION;
import static helper.ConstantHolder.DATA;
import static helper.ConstantHolder.DATE;
import static helper.ConstantHolder.ERROR;
import static helper.ConstantHolder.EVENT;
import static helper.ConstantHolder.LOGIN;
import static helper.ConstantHolder.MESSAGE;
import static helper.ConstantHolder.MESSAGES;
import static helper.ConstantHolder.OK;
import static helper.ConstantHolder.ONLINE_USERS;
import static helper.ConstantHolder.PASSWORD;
import static helper.ConstantHolder.PUBLISH;
import static helper.ConstantHolder.REDIS_ACTION;
import static helper.ConstantHolder.REGISTER;
import static helper.ConstantHolder.ROOM;
import static helper.ConstantHolder.SOCKET_ID;
import static helper.ConstantHolder.STATUS;
import static helper.ConstantHolder.TOKEN;
import static helper.ConstantHolder.WS_ID;
import static helper.ConstantHolder.WS_KEY_HEADER;

/**
 * Вертикл для работы с событиями отправленными с фронта и обратно
 */
public class WebSocketVerticle extends AbstractVerticle {
    private final Map<String, ServerWebSocket> webSocketMap = new HashMap<>();

    /**
     * Метод инициализации
     */
    @Override
    public void start() {
        HttpServer server = vertx.createHttpServer();
        server.webSocketHandler(this::handleWebSocket).listen(8090);
        vertx.eventBus().consumer(UserStatusEvent.STATUS_CHANGED.getValue(), this::handleUserStatusChanged);
    }

    /**
     * Метод прослушивания событий, состояния соединения от пользователя
     * @param ws екзмпляр сокет подключения
     */
    private void handleWebSocket(ServerWebSocket ws) {
        ws.handler(buffer -> {
            String wsKey = ws.headers().get(WS_KEY_HEADER);
            webSocketMap.put(wsKey, ws);
            String message = buffer.toString();
            JsonObject json = new JsonObject(message);
            JsonObject data = json.getJsonObject(DATA);

            switch (WebSocketEvent.fromString(json.getString(EVENT))) {
                case JOIN -> handleJoin(ws, data, wsKey);
                case MESSAGE -> handleMessage(data, ws);
                case LOGIN -> handleLogin(ws, data, wsKey);
                case REGISTER -> handleRegistration(ws, data);
                case GET_ONLINE_USERS -> handleOnlineUsers(ws);
                case AUTH -> handleAuthentication(ws, data, wsKey);
                case GET_MESSAGES -> handleGetMessages(ws, data);
                default -> System.err.println("Unknown event");
            }
        });

        ws.closeHandler(close -> {
            String wsKey = ws.headers().get(WS_KEY_HEADER);
            if (wsKey != null) {
                if (webSocketMap.containsKey(wsKey)) {
                    webSocketMap.remove(wsKey);
                    vertx.eventBus().send(UserStatusEvent.DISCONNECTED.getValue(), new JsonObject().put(SOCKET_ID, wsKey));
                    JsonObject closeMessage = new JsonObject()
                        .put(ACTION, RedisActionEvent.UNSUBSCRIBE.getValue())
                        .put(WS_ID, wsKey);
                    vertx.eventBus().send(REDIS_ACTION, closeMessage);
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

    /**
     * Метод переправки всех сообщений из канала
     * @param ws сокет соединение, по которому можно отправить данные обратно
     * @param data содержимое запроса
     */
    private void handleGetMessages(ServerWebSocket ws, JsonObject data) {
        String room = data.getString(ROOM);
                    JsonObject request = new JsonObject()
                        .put(ACTION, RedisActionEvent.GET_MESSAGES.getValue())
                        .put(ROOM, room);
                    vertx.eventBus().request(REDIS_ACTION, request, reply -> {
                        if (reply.succeeded()) {
                            JsonObject resp = (JsonObject) reply.result().body();
                            JsonArray messages = resp.getJsonArray(MESSAGES);
                            if (messages != null) {
                                ws.writeTextMessage(new JsonObject().put(EVENT, MESSAGES).put(MESSAGES, messages).encode());
                            }
                        } else {
                            ws.writeTextMessage(new JsonObject().put(EVENT, MESSAGES).put(DATA, new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Get messages failed")).encode());
                        }
                    });
    }

    /**
     * Метод проверки токена с новым подключеним
     * @param ws сокет соединение, по которому можно отправить данные обратно
     * @param data токен
     * @param wsKey идентификатор сокета
     */
    private void handleAuthentication(ServerWebSocket ws, JsonObject data, String wsKey) {
        String token = data.getString(TOKEN);
        if (token != null) {
            verifyToken(token, verification -> {
                if (verification.succeeded()) {
                    JsonObject response = verification.result();
                    if (OK.equals(response.getString(STATUS))) {
                        JsonObject principal = response.getJsonObject("principal");
                        String login = principal.getString("sub");
                        vertx.eventBus().send(UserStatusEvent.CONNECTED.getValue(), new JsonObject()
                            .put(LOGIN, login)
                            .put(SOCKET_ID, wsKey));
                    } else {
                        ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Authentication failed").encode());
                    }
                } else {
                    ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Token verification failed").encode());
                }
            });
        }
    }

    /**
     * Метод подписки на новый канал от пользователя
     * @param ws сокет соединение, по которому можно отправить данные обратно
     * @param data содержимое запроса
     * @param wsKey идентификатор сокета
     */
    private void handleJoin(ServerWebSocket ws, JsonObject data, String wsKey) {
        String token = data.getString(TOKEN);
        verifyToken(token, verification -> {
            if (verification.succeeded()) {
                JsonObject response = verification.result();
                if (OK.equals(response.getString(STATUS))) {
                    String user = data.getString("user");
                    String room = data.getString(ROOM);

                    ServerWebSocket existingSocket = webSocketMap.get(wsKey);
                    if (existingSocket != null && !existingSocket.equals(ws)) {
                        existingSocket.close();
                    }
                    JsonObject joinMessage = new JsonObject()
                        .put(ACTION, "subscribe")
                        .put(ROOM, room)
                        .put("user", user)
                        .put(WS_ID, wsKey);
                    vertx.eventBus().send(REDIS_ACTION, joinMessage);
                    vertx.eventBus().consumer("room." + room, message -> {
                        if (ws.equals(webSocketMap.get(wsKey))) {
                            ws.writeTextMessage((String) message.body());
                        }
                    });
                } else {
                    ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Authentication failed").encode());
                }
            } else {
                ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Token verification failed").encode());
            }
        });
    }

    /**
     * Метод отправки сообщения
     * @param data содержимое запроса
     * @param ws сокет соединение, по которому можно отправить данные обратно
     */
    private void handleMessage(JsonObject data, ServerWebSocket ws) {
        String token = data.getString(TOKEN);
        verifyToken(token, verification -> {
            if (verification.succeeded()) {
                JsonObject response = verification.result();
                if (OK.equals(response.getString(STATUS))) {
                    String room = data.getString(ROOM);
                    String login = data.getString(LOGIN);
                    String message = data.getString(MESSAGE);
                    String date = data.getString(DATE);
                    JsonObject publishMessage = new JsonObject()
                        .put(ACTION, PUBLISH)
                        .put(ROOM, room)
                        .put(MESSAGE, new JsonObject()
                            .put(ROOM,room )
                            .put(LOGIN,login )
                            .put(MESSAGE,message )
                            .put(DATE, date).encode());
                    vertx.eventBus().send(REDIS_ACTION, publishMessage);
                } else {
                    ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Authentication failed").encode());
                }
            } else {
                ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Token verification failed").encode());
            }
        });
    }

    /**
     * Метод проверки входа в систему
     * @param ws сокет соединение, по которому можно отправить данные обратно
     * @param data креды пользователя
     * @param wsKey идентификатор вебсокета
     */
    private void handleLogin(ServerWebSocket ws, JsonObject data, String wsKey) {
        String login = data.getString(LOGIN);
        String password = data.getString(PASSWORD);

        JsonObject request = new JsonObject()
            .put(LOGIN, login)
            .put(PASSWORD, password);

        vertx.eventBus().request(AuthEvent.AUTHENTICATE.getValue(), request, reply -> {
            if (reply.succeeded()) {
                JsonObject response = (JsonObject) reply.result().body();
                if (OK.equals(response.getString(STATUS))) {
                    // Handle successful authentication
                    ws.writeTextMessage(new JsonObject().put(STATUS, "authenticated")
                        .put(TOKEN, response.getString(TOKEN))
                        .put("user", login)
                        .encode());
                    vertx.eventBus().send(UserStatusEvent.CONNECTED.getValue(), new JsonObject()
                        .put(LOGIN, login)
                        .put(SOCKET_ID, wsKey));
                } else {
                    // Handle authentication failure
                    ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put(MESSAGE, response.getString(MESSAGE)).encode());
                }
            } else {
                ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Authentication failed").encode());
            }
        });
    }

    /**
     * Метод регистрации нового пользователя
     * @param ws сокет соединение, по которому можно отправить данные обратно
     * @param data креды пользователя
     */
    private void handleRegistration(ServerWebSocket ws, JsonObject data) {
        String login = data.getString(LOGIN);
        String password = data.getString(PASSWORD);

        JsonObject request = new JsonObject()
            .put(ACTION, REGISTER)
            .put(LOGIN, login)
            .put(PASSWORD, password);

        vertx.eventBus().request(AuthEvent.REGISTER.getValue(), request, reply -> {
            if (reply.succeeded()) {
                JsonObject response = (JsonObject) reply.result().body();
                if (OK.equals(response.getString(STATUS))) {

                    ws.writeTextMessage(new JsonObject().put(EVENT, REGISTER).put(DATA, new JsonObject().put(STATUS, OK).put(MESSAGE, "Registration ok")).encode());
                } else {

                    ws.writeTextMessage(new JsonObject().put(EVENT, REGISTER).put(DATA, new JsonObject().put(STATUS, ERROR).put(MESSAGE, response.getString(MESSAGE))).encode());
                }
            } else {
                ws.writeTextMessage(new JsonObject().put(EVENT, REGISTER).put(DATA, new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Registration failed")).encode());
            }
        });
    }

    /**
     * Проверка токена
     * @param token токен
     * @param resultHandler результат проверки
     */
    private void verifyToken(String token, Handler<AsyncResult<JsonObject>> resultHandler) {
        JsonObject request = new JsonObject().put(TOKEN, token);
        vertx.eventBus().request(AuthEvent.VERIFY_TOKEN.getValue(), request, reply -> {
            if (reply.succeeded()) {
                resultHandler.handle(Future.succeededFuture((JsonObject) reply.result().body()));
            } else {
                resultHandler.handle(Future.failedFuture(reply.cause()));
            }
        });
    }

    /**
     * Получение всех пользователей
     * @param ws сокет соединение, по которому можно отправить данные обратно
     */
    private void handleOnlineUsers(ServerWebSocket ws) {
        vertx.eventBus().request(UserStatusEvent.ONLINE.getValue(), null, reply -> {
            if (reply.succeeded()) {
                JsonObject response = (JsonObject) reply.result().body();
                JsonArray onlineUsers = response.getJsonArray(ONLINE_USERS);
                if (onlineUsers != null) {
                    ws.writeTextMessage(new JsonObject().put(EVENT, ONLINE_USERS).put(ONLINE_USERS, onlineUsers).encode());
                } else {
                    ws.writeTextMessage(new JsonObject().put(EVENT, ERROR).put(MESSAGE, "No online users found").encode());
                }
            } else {
                ws.writeTextMessage(new JsonObject().put(EVENT, ERROR).put(MESSAGE, "Failed to get online users").encode());
            }
        });
    }

    /**
     * Оповещение об изменении в массиве онлайн пользователей
     * @param message массив онлайн пользователей
     */
    private void handleUserStatusChanged(Message<JsonObject> message) {
        JsonObject body = message.body();
        String onlineUsers = new JsonObject().put(EVENT, UserStatusEvent.STATUS_CHANGED.getValue()).put(ONLINE_USERS, body.getJsonArray(ONLINE_USERS)).encode();
        for (Map.Entry<String, ServerWebSocket> entry : webSocketMap.entrySet()) {
            ServerWebSocket socket = entry.getValue();
            socket.writeTextMessage(onlineUsers);
        }
    }
}
