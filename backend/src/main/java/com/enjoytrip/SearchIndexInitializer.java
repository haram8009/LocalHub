package com.enjoytrip;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
@Order(100)
public class SearchIndexInitializer implements CommandLineRunner {
 final PostRepository posts; final PostSearchIndexRepository index; final PostSearchIndexer indexer;
 SearchIndexInitializer(PostRepository posts,PostSearchIndexRepository index,PostSearchIndexer indexer) {
  this.posts=posts; this.index=index; this.indexer=indexer;
 }
 @Override @Transactional public void run(String... args) {
  if(SearchIndexState.VERSION.equals(index.version())) return;
  index.clear();
  for(long offset=0;;offset+=100) {
   var batch=posts.scan(offset,100); batch.forEach(indexer::add);
   if(batch.size()<100) break;
  }
  index.saveState();
 }
}
