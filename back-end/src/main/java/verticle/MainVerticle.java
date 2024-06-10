package verticle;

import io.vertx.core.Vertx;


public class MainVerticle {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new JwtAuthVerticle());
        vertx.deployVerticle(new WebSocketVerticle());
        vertx.deployVerticle(new RedisVerticle());
        vertx.deployVerticle(new UserStatusVerticle());
    }
}