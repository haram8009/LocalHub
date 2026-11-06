package com.enjoytrip;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "members")
public class Member {
    @Id @Column(length = 20) String id;
    @Column(nullable = false) String passwordHash;
    @Column(nullable = false, length = 20) String name;
    @Column(nullable = false, unique = true, length = 100) String email;
    @Column(nullable = false) Instant createdAt = Instant.now();
    @Column(nullable = false) long credentialVersion;
    protected Member() {}
    Member(String id, String hash, String name, String email) {
        this.id = id; passwordHash = hash; this.name = name; this.email = email;
    }
}
