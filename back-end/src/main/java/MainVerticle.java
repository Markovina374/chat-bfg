import io.vertx.core.Vertx;
import verticle.JwtAuthVerticle;
import verticle.RedisVerticle;
import verticle.UserStatusVerticle;
import verticle.WebSocketService;


public class MainVerticle {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new JwtAuthVerticle());
        vertx.deployVerticle(new WebSocketService());
        vertx.deployVerticle(new RedisVerticle());
        vertx.deployVerticle(new UserStatusVerticle());
    }
}