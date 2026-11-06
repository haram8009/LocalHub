package com.enjoytrip;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PostSearchIndexer {
    final PostSearchIndexRepository index;
    final SearchTextNormalizer normalizer;
    PostSearchIndexer(PostSearchIndexRepository index, SearchTextNormalizer normalizer) { this.index = index; this.normalizer = normalizer; }
    public void replace(TravelPost post) { index.remove(post.id); add(post); }
    public void add(TravelPost post) {
        Set<String> title = new HashSet<>(normalizer.tokens(post.title)), body = new HashSet<>(normalizer.tokens(post.content));
        Set<String> all = new HashSet<>(title); all.addAll(body);
        for (String term : all) index.add(new PostSearchTerm(term, post, title.contains(term), body.contains(term)));
    }
    public void remove(long id) { index.remove(id); }
    public void removeByAuthor(String id) { index.removeByAuthor(id); }
}
