package com.enjoytrip;

import java.time.Instant;

public class Member {
    String id;
    String passwordHash;
    String name;
    String email;
     Instant createdAt = Instant.now();
    long credentialVersion;
    protected Member() {}
    Member(String id, String hash, String name, String email) {
        this.id = id; passwordHash = hash; this.name = name; this.email = email;
    }
}
