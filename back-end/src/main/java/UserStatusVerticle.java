import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class UserStatusVerticle extends AbstractVerticle {
    private Map<String, String> onlineUsers = new HashMap<>();

    @Override
    public void start() {
        vertx.eventBus().consumer("user.connected", this::handleUserConnected);
        vertx.eventBus().consumer("user.disconnected", this::handleUserDisconnected);
        vertx.eventBus().consumer("user.getOnline", this::handleGetOnlineUsers);
    }

    private void handleUserConnected(Message<JsonObject> message) {
        String userId = message.body().getString("login");
        String socketId = message.body().getString("socketId");
        onlineUsers.put(userId, socketId);
        notifyUserStatusChanged();
    }

    private void handleUserDisconnected(Message<JsonObject> message) {
        String socketId = message.body().getString("socketId");
        onlineUsers.entrySet().stream()
            .filter(x -> x.getValue().equals(socketId))
            .findFirst()
            .ifPresent(x -> onlineUsers.remove(x.getKey()));
        notifyUserStatusChanged();
    }

    private void handleGetOnlineUsers(Message<JsonObject> message) {
        JsonArray onlineUsersArray = new JsonArray();
        onlineUsers.keySet().forEach(onlineUsersArray::add);
        message.reply(new JsonObject().put("onlineUsers", onlineUsersArray));
    }

    private void notifyUserStatusChanged() {
        vertx.eventBus().publish("user.statusChanged", new JsonObject().put("onlineUsers", onlineUsers));
    }
}