package org.edmund.brokeai.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class Argon2idMigratingPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
    private final PasswordEncoder legacyBcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return argon2.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith("$argon2")) {
            return argon2.matches(rawPassword, encodedPassword);
        }
        if (encodedPassword.startsWith("$2")) {
            return legacyBcrypt.matches(rawPassword, encodedPassword);
        }
        return false;
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword != null && !encodedPassword.startsWith("$argon2id$");
    }
}
