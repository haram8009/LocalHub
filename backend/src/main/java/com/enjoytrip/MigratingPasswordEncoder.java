package com.enjoytrip;

import java.util.Map;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;

/** New hashes use Argon2id; existing BCrypt accounts upgrade after a successful login. */
public final class MigratingPasswordEncoder implements PasswordEncoder {
    private final DelegatingPasswordEncoder delegate;
    public MigratingPasswordEncoder() {
        var bcrypt = new BCryptPasswordEncoder();
        delegate = new DelegatingPasswordEncoder("argon2id-v1", Map.of(
            "argon2id-v1", new Argon2PasswordEncoder(16, 32, 1, 19456, 2), "bcrypt", bcrypt));
        delegate.setDefaultPasswordEncoderForMatches(bcrypt);
    }
    @Override public String encode(CharSequence raw) { return delegate.encode(raw); }
    @Override public boolean matches(CharSequence raw, String encoded) {
        if (raw == null || encoded == null || encoded.isBlank()) return false;
        if (!encoded.startsWith("{argon2id-v1}") && !encoded.startsWith("{bcrypt}") && !encoded.startsWith("$2")) return false;
        try { return delegate.matches(raw, encoded); }
        catch (IllegalArgumentException | IndexOutOfBoundsException e) { return false; }
    }
    @Override public boolean upgradeEncoding(String encoded) {
        return delegate.upgradeEncoding(encoded);
    }
}
