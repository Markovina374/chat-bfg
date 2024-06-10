package helper;

import java.util.Arrays;

public enum UserStatusEvent {

    CONNECTED("user.connected"),
    DISCONNECTED("user.disconnected"),
    ONLINE("user.getOnline"),
    STATUS_CHANGED("user.statusChanged");


    public String getValue() {
        return value;
    }


    private final String value;
    UserStatusEvent(String value) {
        this.value = value;
    }

    public static UserStatusEvent fromString(String str) {
        return Arrays.stream(UserStatusEvent.values())
            .filter(x -> x.value.equals(str))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Нет такого типа"));
    }
}
