import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.KeyStoreOptions;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JwtAuthVerticle extends AbstractVerticle {
    private JWTAuth jwtAuth;

    @Override
    public void start() {
        jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setBuffer("keyboard cat")));

        vertx.eventBus().consumer("auth.register", this::handleRegister);
        vertx.eventBus().consumer("auth.authenticate", this::handleAuthenticate);
        vertx.eventBus().consumer("auth.verifyToken", this::handleVerifyToken);

    }

    private void handleRegister(Message<JsonObject> message) {
        JsonObject data = message.body();
        String login = data.getString("login");
        String password = data.getString("password");

        JsonObject request = new JsonObject()
            .put("action", "register")
            .put("login", login)
            .put("password", PasswordHelper.encode(password.getBytes(StandardCharsets.UTF_8)));

        vertx.eventBus().request("redis.auth", request, reply -> {
            if (reply.succeeded()) {
                message.reply(new JsonObject().put("status", "ok"));
            } else {
                message.reply(new JsonObject().put("status", "error").put("message", "Registration failed"));
            }
        });
    }

    private void handleAuthenticate(Message<JsonObject> message) {
        JsonObject data = message.body();
        String login = data.getString("login");
        String password = data.getString("password");

        JsonObject request = new JsonObject()
            .put("action", "authenticate")
            .put("login", login)
            .put("password", password);

        vertx.eventBus().request("redis.auth", request, reply -> {
            if (reply.succeeded()) {
                JsonObject response = (JsonObject) reply.result().body();
                if ("ok".equals(response.getString("status"))) {
                    String token = jwtAuth.generateToken(
                        new JsonObject().put("sub", login),
                        new JWTOptions().setExpiresInMinutes(60));
                    message.reply(new JsonObject().put("status", "ok").put("token", token));
                } else {
                    message.reply(new JsonObject().put("status", "error").put("message", "Invalid credentials"));
                }
            } else {
                message.reply(new JsonObject().put("status", "error").put("message", "Authentication failed"));
            }
        });
    }

    private void handleVerifyToken(Message<JsonObject> message) {
        String token = message.body().getString("token");
        jwtAuth.authenticate(new JsonObject().put("token", token), res -> {
            if (res.succeeded()) {
                message.reply(new JsonObject().put("status", "ok").put("principal", res.result().principal()));
            } else {
                message.reply(new JsonObject().put("status", "error").put("message", "Invalid token"));
            }
        });
    }
}
