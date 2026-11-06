package com.enjoytrip;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "post_search_terms", uniqueConstraints = @UniqueConstraint(name = "uk_search_hash_post", columnNames = {"term_hash", "post_id"}),
       indexes = @Index(name = "idx_search_post", columnList = "post_id"))
public class PostSearchTerm {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "search_term_seq")
    @SequenceGenerator(name = "search_term_seq", allocationSize = 50)
    Long id;
    // Preserve long tokens too: NFKC can expand the original 20,000-character body.
    @Column(nullable = false, length = 400000) String term;
    // MySQL cannot uniquely index a LONGTEXT token. Keep the full token for substring search.
    @Column(name = "term_hash", length = 64) String termHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    TravelPost post;
    @Column(nullable = false) boolean titleHit;
    @Column(nullable = false) boolean bodyHit;
    protected PostSearchTerm() {}
    PostSearchTerm(String term, TravelPost post, boolean titleHit, boolean bodyHit) {
        this.term = term; this.post = post; this.titleHit = titleHit; this.bodyHit = bodyHit;
        try {
            this.termHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(term.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
