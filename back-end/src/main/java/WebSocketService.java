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
import java.util.Set;
import java.util.stream.Collectors;

public class WebSocketService extends AbstractVerticle {
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
                case "auth":
                    handleAuthentication(ws, data);
                    break;
                case "register":
                    handleRegistration(ws, data);
                    break;
                case "getOnlineUsers":
                    handleOnlineUsers(ws);
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
                    vertx.eventBus().send("user.disconnected", wsKey);
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
        String token = ws.headers().get("jwt_token");

        // Verify token before sending message
        verifyToken(token, verification -> {
            if (verification.succeeded()) {
                JsonObject response = verification.result();
                if ("ok".equals(response.getString("status"))) {
                    String wsKey = ws.headers().get("Sec-WebSocket-Key");
                    if (wsKey != null) {
                        String user = data.getString("user");
                        String room = data.getString("room");

                        ServerWebSocket existingSocket = webSocketMap.get(wsKey);
                        if (existingSocket != null && !existingSocket.equals(ws)) {
                            existingSocket.close();
                        }

                        webSocketMap.put(wsKey, ws);
                        System.out.println("User " + user + "joined room: " + room);

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
                } else {
                    // Token verification failed, send error message to client
                    ws.writeTextMessage(new JsonObject().put("status", "error").put("message", "Authentication failed").encode());
                }
            } else {
                // Failed to verify token, send error message to client
                ws.writeTextMessage(new JsonObject().put("status", "error").put("message", "Token verification failed").encode());
            }
        });
    }

    private void handleMessage(JsonObject data, ServerWebSocket ws) {
        String token = ws.headers().get("jwt_token");

        // Verify token before sending message
        verifyToken(token, verification -> {
            if (verification.succeeded()) {
                JsonObject response = verification.result();
                if ("ok".equals(response.getString("status"))) {
                    // Token is valid, proceed with sending the message
                    String room = data.getString("room");
                    String message = data.encode();

                    // Forward message to RedisVerticle for publishing
                    JsonObject publishMessage = new JsonObject()
                        .put("action", "publish")
                        .put("room", room)
                        .put("message", message);
                    vertx.eventBus().send("redis.action", publishMessage);
                    System.out.println("Sent publish message for room: " + room);
                } else {
                    // Token verification failed, send error message to client
                    ws.writeTextMessage(new JsonObject().put("status", "error").put("message", "Authentication failed").encode());
                }
            } else {
                // Failed to verify token, send error message to client
                ws.writeTextMessage(new JsonObject().put("status", "error").put("message", "Token verification failed").encode());
            }
        });
    }

    private void handleAuthentication(ServerWebSocket ws, JsonObject data) {
        String login = data.getString("login");
        String password = data.getString("password");

        JsonObject request = new JsonObject()
            .put("login", login)
            .put("password", password);

        vertx.eventBus().request("auth.authenticate", request, reply -> {
            if (reply.succeeded()) {
                JsonObject response = (JsonObject) reply.result().body();
                if ("ok".equals(response.getString("status"))) {
                    // Handle successful authentication
                    ws.writeTextMessage(new JsonObject().put("status", "authenticated")
                        .put("token", response.getString("token"))
                            .put("user", login)
                        .encode());
                    vertx.eventBus().send("user.connected", new JsonObject()
                        .put("login", login)
                        .put("socketId", ws.headers().get("Sec-WebSocket-Key")));
                    webSocketMap.put(ws.headers().get("Sec-WebSocket-Key"), ws);
                } else {
                    // Handle authentication failure
                    ws.writeTextMessage(new JsonObject().put("status", "error").put("message", response.getString("message")).encode());
                }
            } else {
                ws.writeTextMessage(new JsonObject().put("status", "error").put("message", "Authentication failed").encode());
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
                if ("ok".equals(response.getString("status"))) {
                    // Handle successful registration
                    ws.writeTextMessage(new JsonObject().put("status", "registered").encode());
                } else {
                    // Handle registration failure
                    ws.writeTextMessage(new JsonObject().put("status", "error").put("message", response.getString("message")).encode());
                }
            } else {
                ws.writeTextMessage(new JsonObject().put("status", "error").put("message", "Registration failed").encode());
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
                    ws.writeTextMessage(new JsonObject().put("event", "error").put("message", "No online users found").encode());
                }
            } else {
                ws.writeTextMessage(new JsonObject().put("event", "error").put("message", "Failed to get online users").encode());
            }
        });
    }

    private void handleUserStatusChanged(Message<JsonObject> message) {
        JsonObject body = message.body();
        JsonObject onlineUsersJson = body.getJsonObject("onlineUsers");
        String encode = new JsonObject().put("event", "user.statusChanged").put("onlineUsers", onlineUsersJson).encode();

        for (Map.Entry<String, ServerWebSocket> entry : webSocketMap.entrySet()) {
            ServerWebSocket socket = entry.getValue();
            socket.writeTextMessage(encode);
        }
    }
}
