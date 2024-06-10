package helper;

import java.util.Arrays;

public enum RedisAuthAction {
    REGISTER("register"),
    AUTHENTICATE("authenticate");

    public String getValue() {
        return value;
    }


    private final String value;
    RedisAuthAction(String value) {
        this.value = value;
    }

    public static RedisAuthAction fromString(String str) {
        return Arrays.stream(RedisAuthAction.values())
            .filter(x -> x.value.equals(str))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Нет такого типа"));
    }
}
