package verticle;

import helper.UserStatusEvent;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

import static helper.ConstantHolder.LOGIN;
import static helper.ConstantHolder.ONLINE_USERS;
import static helper.ConstantHolder.SOCKET_ID;


/**
 * Вертикл отвечающий за логику показа и актуализацию онлайн пользователей
 */
public class UserStatusVerticle extends AbstractVerticle {
    private static Map<String, String> onlineUsers = new HashMap<>();

    /**
     * Метод инициализации
     */
    @Override
    public void start() {
        vertx.eventBus().consumer(UserStatusEvent.CONNECTED.getValue(), this::handleUserConnected);
        vertx.eventBus().consumer(UserStatusEvent.DISCONNECTED.getValue(), this::handleUserDisconnected);
        vertx.eventBus().consumer(UserStatusEvent.ONLINE.getValue(), this::handleGetOnlineUsers);
    }

    /**
     * Метод обработки онлайн подключения
     *
     * @param message логин и идентификатор сокета
     */
    private void handleUserConnected(Message<JsonObject> message) {
        String userId = message.body().getString(LOGIN);
        String socketId = message.body().getString(SOCKET_ID);
        onlineUsers.put(userId, socketId);
        notifyUserStatusChanged();
    }

    /**
     * Метод обработки выхода пользователя из сети
     *
     * @param message идентификатор сокета
     */
    private void handleUserDisconnected(Message<JsonObject> message) {
        String socketId = message.body().getString(SOCKET_ID);
        onlineUsers.entrySet().stream()
            .filter(x -> x.getValue().equals(socketId))
            .findFirst()
            .ifPresent(x -> onlineUsers.remove(x.getKey()));
        notifyUserStatusChanged();
    }


    /**
     * Метод получение всех онлайн пользователей
     *
     * @param message сообщение для ответа
     */
    private void handleGetOnlineUsers(Message<JsonObject> message) {
        JsonArray onlineUsersArray = new JsonArray();
        onlineUsers.keySet().forEach(onlineUsersArray::add);
        message.reply(new JsonObject().put(ONLINE_USERS, onlineUsersArray));
    }

    /**
     * Метод оповещения об изменении онлайн пользователей
     */
    private void notifyUserStatusChanged() {
        JsonArray onlineUsersArray = new JsonArray();
        onlineUsers.keySet().forEach(onlineUsersArray::add);
        vertx.eventBus().publish(UserStatusEvent.STATUS_CHANGED.getValue(), new JsonObject().put(ONLINE_USERS, onlineUsersArray));
    }
}