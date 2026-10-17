import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

final class PasswordUtil {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    static PasswordHash hash(char[] password) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return new PasswordHash(salt, derive(password, salt));
    }

    static boolean verify(char[] password, byte[] salt, byte[] expectedHash) {
        return MessageDigest.isEqual(derive(password, salt), expectedHash);
    }

    private static byte[] derive(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("This Java runtime does not support PBKDF2WithHmacSHA256.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    static byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }

    record PasswordHash(byte[] salt, byte[] hash) {
    }
}
