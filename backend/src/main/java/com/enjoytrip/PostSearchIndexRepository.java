package com.enjoytrip;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class PostSearchIndexRepository {
    final EntityManager em;
    PostSearchIndexRepository(EntityManager em) { this.em = em; }
    public record Posting(String term, long postId, boolean titleHit, boolean bodyHit) {}
    public List<Posting> findContaining(List<String> queries) {
        var clauses = new ArrayList<String>();
        for (int i = 0; i < queries.size(); i++) clauses.add("t.term like :q" + i + " escape '\\'");
        var query = em.createQuery("select t.term, t.post.id, t.titleHit, t.bodyHit from PostSearchTerm t where "
            + String.join(" or ", clauses), Object[].class);
        for (int i = 0; i < queries.size(); i++) query.setParameter("q" + i, SearchTextNormalizer.containsPattern(queries.get(i)));
        return query.getResultList().stream().map(row -> new Posting((String) row[0], (Long) row[1], (Boolean) row[2], (Boolean) row[3])).toList();
    }
    void add(PostSearchTerm term) { em.persist(term); }
    void remove(long id) { em.createQuery("delete from PostSearchTerm t where t.post.id = :id").setParameter("id", id).executeUpdate(); }
    void removeByAuthor(String id) { em.createQuery("delete from PostSearchTerm t where t.post.authorId = :id").setParameter("id", id).executeUpdate(); }
    void clear() { em.createQuery("delete from PostSearchTerm").executeUpdate(); }
}
