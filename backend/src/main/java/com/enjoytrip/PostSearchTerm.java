package com.enjoytrip;

public class PostSearchTerm {

    Long id;
    // Preserve long tokens too: NFKC can expand the original 20,000-character body.
    String term;
    // MySQL cannot uniquely index a LONGTEXT token. Keep the full token for substring search.
    String termHash;

    TravelPost post;
    boolean titleHit;
    boolean bodyHit;
    protected PostSearchTerm() {}
    PostSearchTerm(String term, TravelPost post, boolean titleHit, boolean bodyHit) {
        this.term = term; this.post = post; this.titleHit = titleHit; this.bodyHit = bodyHit;
        try {
            this.termHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(term.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
