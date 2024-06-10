package verticle;

import helper.AuthEvent;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.JWTOptions;
import helper.PasswordHelper;

import java.nio.charset.StandardCharsets;

import static helper.ConstantHolder.ACTION;
import static helper.ConstantHolder.ERROR;
import static helper.ConstantHolder.LOGIN;
import static helper.ConstantHolder.MESSAGE;
import static helper.ConstantHolder.OK;
import static helper.ConstantHolder.PASSWORD;
import static helper.ConstantHolder.REDIS_AUTH;
import static helper.ConstantHolder.STATUS;
import static helper.ConstantHolder.TOKEN;

/**
 * Verticle отвечающий за Аутентификацию и Регистрацию новых пользователей
 */
public class JwtAuthVerticle extends AbstractVerticle {
    private JWTAuth jwtAuth;

    /**
     * Стандартный метод инициализации
     */
    @Override
    public void start() {
        jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setBuffer("keyboard cat"))); //todo вынести в отдельный файл

        vertx.eventBus().consumer(AuthEvent.REGISTER.getValue(), this::handleRegister);
        vertx.eventBus().consumer(AuthEvent.AUTHENTICATE.getValue(), this::handleAuthenticate);
        vertx.eventBus().consumer(AuthEvent.VERIFY_TOKEN.getValue(), this::handleVerifyToken);

    }

    /**
     * Метод прослушивания события регистрации
     * @param message креды пользователя
     */
    private void handleRegister(Message<JsonObject> message) {
        JsonObject data = message.body();
        String login = data.getString(LOGIN);
        String password = data.getString(PASSWORD);

        JsonObject request = new JsonObject()
            .put(ACTION, "register")
            .put(LOGIN, login)
            .put(PASSWORD, PasswordHelper.encode(password.getBytes(StandardCharsets.UTF_8)));

        vertx.eventBus().request(REDIS_AUTH, request, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                if(ERROR.equals(body.getString(STATUS))) {
                    message.reply(new JsonObject().put(STATUS, ERROR).put(MESSAGE, body.getString(MESSAGE)));
                } else {
                    message.reply(new JsonObject().put(STATUS, OK));
                }
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Registration failed"));
            }
        });
    }

    /**
     * Метод прослушивания события Аутентификации
     * @param message креды пользователя
     */
    private void handleAuthenticate(Message<JsonObject> message) {
        JsonObject data = message.body();
        String login = data.getString(LOGIN);
        String password = data.getString(PASSWORD);

        JsonObject request = new JsonObject()
            .put(ACTION, "authenticate")
            .put(LOGIN, login)
            .put(PASSWORD, password);

        vertx.eventBus().request(REDIS_AUTH, request, reply -> {
            if (reply.succeeded()) {
                JsonObject response = (JsonObject) reply.result().body();
                if (OK.equals(response.getString(STATUS))) {
                    String token = jwtAuth.generateToken(
                        new JsonObject().put("sub", login),
                        new JWTOptions().setExpiresInMinutes(60));
                    message.reply(new JsonObject().put(STATUS, OK).put(TOKEN, token));
                } else {
                    message.reply(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Invalid credentials"));
                }
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Authentication failed"));
            }
        });
    }

    /**
     * Метод для проверки токена
     * @param message токен
     */
    private void handleVerifyToken(Message<JsonObject> message) {
        String token = message.body().getString(TOKEN);
        token = token.replaceAll("\\\"", "");
        jwtAuth.authenticate(new JsonObject().put(TOKEN, token), res -> {
            if (res.succeeded()) {
                message.reply(new JsonObject().put(STATUS, OK).put("principal", res.result().principal()));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put(MESSAGE, "Invalid token"));
            }
        });
    }
}
