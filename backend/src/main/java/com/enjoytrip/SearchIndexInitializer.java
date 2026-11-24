package com.enjoytrip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
@Order(100)
public class SearchIndexInitializer implements CommandLineRunner {
 private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);
 final PostRepository posts; final PostSearchIndexRepository index; final PostSearchIndexer indexer;
 SearchIndexInitializer(PostRepository posts,PostSearchIndexRepository index,PostSearchIndexer indexer) {
  this.posts=posts; this.index=index; this.indexer=indexer;
 }
 @Override @Transactional public void run(String... args) {
  if(SearchIndexState.VERSION.equals(index.version())) {
   // 색인은 그대로 두고 단어 사전만 맞춥니다. 사전이 없던 버전(V2 이전)이 쓴 글의 단어도 여기서 채워집니다.
   int added=index.syncTerms();
   if(added>0) log.info("검색 단어 사전 동기화: {}개 추가", added);
   return;
  }
  index.clear();
  for(long offset=0;;offset+=100) {
   var batch=posts.scan(offset,100); batch.forEach(indexer::add);
   if(batch.size()<100) break;
  }
  index.saveState();
 }
}
