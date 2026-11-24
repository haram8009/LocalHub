package com.enjoytrip;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class PostSearchIndexRepository {
 final SearchIndexMapper mapper;
 PostSearchIndexRepository(SearchIndexMapper mapper) { this.mapper=mapper; }
 public record Posting(String term,long postId,boolean titleHit,boolean bodyHit) {}
 public List<Posting> findContaining(List<String> queries) {
  if(queries.isEmpty()) return List.of();
  return mapper.findContaining(queries.stream().map(q->"%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%").toList());
 }
 void add(PostSearchTerm term) { mapper.addTerm(term); mapper.add(term); }
 /** 사전에 빠진 단어를 postings 에서 채우고 추가한 개수를 돌려줍니다. */
 int syncTerms() { return mapper.syncTerms(); }
 void remove(long id) { mapper.remove(id); }
 void removeByAuthor(String id) { mapper.removeByAuthor(id); }
 void clear() { mapper.clear(); mapper.clearTerms(); }
 String version() { return mapper.version(); }
 void deleteState() { mapper.deleteState(); }
 void saveState() { mapper.deleteState(); mapper.insertState(SearchIndexState.VERSION); }
}
