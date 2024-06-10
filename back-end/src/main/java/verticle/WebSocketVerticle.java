package verticle;

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

import static helper.ConstantHolder.ERROR;
import static helper.ConstantHolder.STATUS;

public class WebSocketVerticle extends AbstractVerticle {
    private final Map<String, ServerWebSocket> webSocketMap = new HashMap<>();

    @Override
    public void start() {
        HttpServer server = vertx.createHttpServer();
        server.webSocketHandler(this::handleWebSocket).listen(8090);
        vertx.eventBus().consumer("user.statusChanged", this::handleUserStatusChanged);
    }

    private void handleWebSocket(ServerWebSocket ws) {
        System.out.println("WebSocket connection opened");

        ws.handler(buffer -> {
            String wsKey = ws.headers().get("Sec-WebSocket-Key");
            webSocketMap.put(wsKey, ws);
            String message = buffer.toString();
            System.out.println("Received message: " + message);
            JsonObject json = new JsonObject(message);
            JsonObject data = json.getJsonObject("data");

            switch (WebSocketEvent.fromString(json.getString("event"))) {
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
            System.out.println("WebSocket connection closed");

            String wsKey = ws.headers().get("Sec-WebSocket-Key");
            if (wsKey != null) {
                if (webSocketMap.containsKey(wsKey)) {
                    webSocketMap.remove(wsKey);
                    vertx.eventBus().send("user.disconnected", new JsonObject().put("socketId", wsKey));
                    // Notify verticle.RedisVerticle to unsubscribe
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

    private void handleGetMessages(ServerWebSocket ws, JsonObject data) {
        String room = data.getString("room");
                    JsonObject request = new JsonObject()
                        .put("action", "getMessages")
                        .put("room", room);
                    vertx.eventBus().request("redis.action", request, reply -> {
                        if (reply.succeeded()) {
                            JsonObject resp = (JsonObject) reply.result().body();
                            JsonArray messages = resp.getJsonArray("messages");
                            if (messages != null) {
                                ws.writeTextMessage(new JsonObject().put("event", "messages").put("messages", messages).encode());
                            }
                        } else {
                            ws.writeTextMessage(new JsonObject().put("event", "messages").put("data", new JsonObject().put(STATUS, ERROR).put("message", "Get messages failed")).encode());
                        }
                    });
    }

    private void handleAuthentication(ServerWebSocket ws, JsonObject data, String wsKey) {
        String token = data.getString("token");
        System.out.println(token);
        if (token != null) {
            verifyToken(token, verification -> {
                if (verification.succeeded()) {
                    JsonObject response = verification.result();
                    if ("ok".equals(response.getString(STATUS))) {
                        JsonObject principal = response.getJsonObject("principal");
                        String login = principal.getString("sub");
                        vertx.eventBus().send("user.connected", new JsonObject()
                            .put("login", login)
                            .put("socketId", wsKey));
                    } else {
                        // Token verification failed, send error message to client
                        ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put("message", "Authentication failed").encode());
                    }
                } else {
                    // Failed to verify token, send error message to client
                    ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put("message", "Token verification failed").encode());
                }
            });
        }
    }


    private void handleJoin(ServerWebSocket ws, JsonObject data, String wsKey) {
        String token = data.getString("token");

        // Verify token before sending message
        verifyToken(token, verification -> {
            if (verification.succeeded()) {
                JsonObject response = verification.result();
                if ("ok".equals(response.getString(STATUS))) {
                    String user = data.getString("user");
                    String room = data.getString("room");

                    ServerWebSocket existingSocket = webSocketMap.get(wsKey);
                    if (existingSocket != null && !existingSocket.equals(ws)) {
                        existingSocket.close();
                    }

                    System.out.println("User " + user + "joined room: " + room);

                    // Notify verticle.RedisVerticle to subscribe to the room
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
                    // Token verification failed, send error message to client
                    ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put("message", "Authentication failed").encode());
                }
            } else {
                // Failed to verify token, send error message to client
                ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put("message", "Token verification failed").encode());
            }
        });
    }

    private void handleMessage(JsonObject data, ServerWebSocket ws) {
        String token = data.getString("token");

        // Verify token before sending message
        verifyToken(token, verification -> {
            if (verification.succeeded()) {
                JsonObject response = verification.result();
                if ("ok".equals(response.getString(STATUS))) {
                    // Token is valid, proceed with sending the message
                    String room = data.getString("room");
                    String login = data.getString("login");
                    String message = data.getString("message");
                    String date = data.getString("date");
                    // Forward message to verticle.RedisVerticle for publishing
                    JsonObject publishMessage = new JsonObject()
                        .put("action", "publish")
                        .put("room", room)
                        .put("message", new JsonObject()
                            .put("room",room )
                            .put("login",login )
                            .put("message",message )
                            .put("date", date).encode());
                    vertx.eventBus().send("redis.action", publishMessage);
                    System.out.println("Sent publish message for room: " + room);
                } else {
                    // Token verification failed, send error message to client
                    ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put("message", "Authentication failed").encode());
                }
            } else {
                // Failed to verify token, send error message to client
                ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put("message", "Token verification failed").encode());
            }
        });
    }

    private void handleLogin(ServerWebSocket ws, JsonObject data, String wsKey) {
        String login = data.getString("login");
        String password = data.getString("password");

        JsonObject request = new JsonObject()
            .put("login", login)
            .put("password", password);

        vertx.eventBus().request("auth.authenticate", request, reply -> {
            if (reply.succeeded()) {
                JsonObject response = (JsonObject) reply.result().body();
                if ("ok".equals(response.getString(STATUS))) {
                    // Handle successful authentication
                    ws.writeTextMessage(new JsonObject().put(STATUS, "authenticated")
                        .put("token", response.getString("token"))
                        .put("user", login)
                        .encode());
                    vertx.eventBus().send("user.connected", new JsonObject()
                        .put("login", login)
                        .put("socketId", wsKey));
                } else {
                    // Handle authentication failure
                    ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put("message", response.getString("message")).encode());
                }
            } else {
                ws.writeTextMessage(new JsonObject().put(STATUS, ERROR).put("message", "Authentication failed").encode());
            }
        });
    }


    private void handleRegistration(ServerWebSocket ws, JsonObject data) {
        String login = data.getString("login");
        String password = data.getString("password");

        JsonObject request = new JsonObject()
            .put("action", "register")
            .put("login", login)
            .put("password", password);

        vertx.eventBus().request("auth.register", request, reply -> {
            if (reply.succeeded()) {
                JsonObject response = (JsonObject) reply.result().body();
                if ("ok".equals(response.getString(STATUS))) {
                    // Handle successful registration
                    ws.writeTextMessage(new JsonObject().put("event", "register").put("data", new JsonObject().put(STATUS, "ok").put("message", "Registration ok")).encode());
                } else {
                    // Handle registration failure
                    ws.writeTextMessage(new JsonObject().put("event", "register").put("data", new JsonObject().put(STATUS, ERROR).put("message", response.getString("message"))).encode());
                }
            } else {
                ws.writeTextMessage(new JsonObject().put("event", "register").put("data", new JsonObject().put(STATUS, ERROR).put("message", "Registration failed")).encode());
            }
        });
    }

    private void verifyToken(String token, Handler<AsyncResult<JsonObject>> resultHandler) {
        JsonObject request = new JsonObject().put("token", token);
        vertx.eventBus().request("auth.verifyToken", request, reply -> {
            if (reply.succeeded()) {
                resultHandler.handle(Future.succeededFuture((JsonObject) reply.result().body()));
            } else {
                resultHandler.handle(Future.failedFuture(reply.cause()));
            }
        });
    }


    private void handleOnlineUsers(ServerWebSocket ws) {
        vertx.eventBus().request("user.getOnline", null, reply -> {
            if (reply.succeeded()) {
                JsonObject response = (JsonObject) reply.result().body();
                JsonArray onlineUsers = response.getJsonArray("onlineUsers");
                if (onlineUsers != null) {
                    ws.writeTextMessage(new JsonObject().put("event", "onlineUsers").put("onlineUsers", onlineUsers).encode());
                } else {
                    ws.writeTextMessage(new JsonObject().put("event", ERROR).put("message", "No online users found").encode());
                }
            } else {
                ws.writeTextMessage(new JsonObject().put("event", ERROR).put("message", "Failed to get online users").encode());
            }
        });
    }

    private void handleUserStatusChanged(Message<JsonObject> message) {
        JsonObject body = message.body();
        String onlineUsers = new JsonObject().put("event", "user.statusChanged").put("onlineUsers", body.getJsonArray("onlineUsers")).encode();
        System.out.println("Поменялися пользаки" + onlineUsers);
        for (Map.Entry<String, ServerWebSocket> entry : webSocketMap.entrySet()) {
            ServerWebSocket socket = entry.getValue();
            socket.writeTextMessage(onlineUsers);
        }
    }
}
