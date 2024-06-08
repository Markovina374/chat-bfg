import io.vertx.core.Vertx;


public class MainVerticle {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new JwtAuthVerticle());
        vertx.deployVerticle(new WebSocketService());
        vertx.deployVerticle(new RedisVerticle());

    }
}