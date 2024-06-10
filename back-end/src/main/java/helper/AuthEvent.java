package helper;

import java.util.Arrays;

public enum AuthEvent {
    REGISTER("auth.register"),
    AUTHENTICATE("auth.authenticate"),
    VERIFY_TOKEN("auth.verifyToken");

    public String getValue() {
        return value;
    }


    private final String value;
    AuthEvent(String value) {
        this.value = value;
    }

    public static AuthEvent fromString(String str) {
        return Arrays.stream(AuthEvent.values())
            .filter(x -> x.value.equals(str))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Нет такого типа"));
    }

}
