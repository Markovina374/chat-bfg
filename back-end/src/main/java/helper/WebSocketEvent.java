package helper;

import java.util.Arrays;

public enum WebSocketEvent {
    JOIN("join"),
    MESSAGE("message"),
    LOGIN("login"),
    REGISTER("register"),
    GET_ONLINE_USERS("getOnlineUsers"),
    AUTH("auth"),
    GET_MESSAGES("getMessages");

    private final String value;
    WebSocketEvent(String value) {
        this.value = value;
    }

    public static WebSocketEvent fromString(String str) {
        return Arrays.stream(WebSocketEvent.values())
            .filter(x -> x.value.equals(str))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Нет такого типа"));
    }
}
