import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PasswordHelper {
    static String encode(byte[] bytes) {
        return new String(Base64.getEncoder().encode(bytes), StandardCharsets.UTF_8);
    }

    static boolean isValid (byte[] bytes, String pass) {
        return new String(Base64.getDecoder().decode(bytes), StandardCharsets.UTF_8).equals(pass);
    }

    private PasswordHelper() {
    }
}
