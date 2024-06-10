package helper;

import java.util.Arrays;

public enum RedisActionEvent {
    SUBSCRIBE("subscribe"),
    UNSUBSCRIBE("unsubscribe"),
    PUBLISH("publish"),
    GET_MESSAGES("getMessages");


    private final String value;

    RedisActionEvent(String value) {
        this.value = value;
    }


    public String getValue() {
        return value;
    }

    public static RedisActionEvent fromString(String str) {
        return Arrays.stream(RedisActionEvent.values())
            .filter(x -> x.value.equals(str))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Нет такого типа"));
    }
    }
