package com.enjoytrip;

import jakarta.persistence.*;

@Entity
@Table(name = "search_index_state")
public class SearchIndexState {
    static final String VERSION = "nfkc-substring-v2-sha256";
    @Id String name;
    @Column(nullable = false) String version;
    protected SearchIndexState() { name = "posts"; version = VERSION; }
}
