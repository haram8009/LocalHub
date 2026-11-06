package com.enjoytrip;

import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
public class SearchIndexInitializer implements CommandLineRunner {
    final EntityManager em;
    final PostRepository posts;
    final PostSearchIndexRepository index;
    final PostSearchIndexer indexer;
    SearchIndexInitializer(EntityManager em, PostRepository posts, PostSearchIndexRepository index, PostSearchIndexer indexer) {
        this.em = em; this.posts = posts; this.index = index; this.indexer = indexer;
    }
    @Override @Transactional
    public void run(String... args) {
        var state = em.find(SearchIndexState.class, "posts");
        if (state != null && SearchIndexState.VERSION.equals(state.version)) return;
        index.clear();
        for (int page = 0; ; page++) {
            var slice = posts.findAll(PageRequest.of(page, 100, Sort.by("id")));
            slice.forEach(indexer::add);
            em.flush(); em.clear();
            if (!slice.hasNext()) break;
        }
        em.merge(new SearchIndexState());
    }
}
