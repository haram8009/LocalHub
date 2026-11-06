package com.enjoytrip;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class MigratingPasswordEncoderTest {
    final MigratingPasswordEncoder encoder = new MigratingPasswordEncoder();
    @Test void usesRandomSaltAndProposalCostParameters() {
        String first = encoder.encode("Trip1234!"), second = encoder.encode("Trip1234!");
        assertThat(first).startsWith("{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("Trip1234!", first)).isTrue();
        assertThat(encoder.matches("Trip1234!", second)).isTrue();
        assertThat(encoder.matches("wrong", first)).isFalse();
        assertThat(encoder.upgradeEncoding(first)).isFalse();
    }
    @Test void keepsExistingBcryptAccountsCompatibleAndRequestsUpgrade() {
        String old = new BCryptPasswordEncoder().encode("Trip1234!");
        assertThat(encoder.matches("Trip1234!", old)).isTrue();
        assertThat(encoder.matches("Trip1234!", "{bcrypt}" + old)).isTrue();
        assertThat(encoder.upgradeEncoding(old)).isTrue();
        assertThat(encoder.matches("wrong", old)).isFalse();
    }
    @Test void rejectsUnsupportedAndCorruptHashesSafely() {
        for (String stored : new String[]{"", "plain", "{unknown}value", "{argon2id-v1}broken", "{argon2id-v1}$argon2id$v=19$invalid", "$2bad"})
            assertThat(encoder.matches("Trip1234!", stored)).as("stored=%s", stored).isFalse();
        assertThat(encoder.matches("Trip1234!", null)).isFalse();
    }
}
