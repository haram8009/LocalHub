package com.enjoytrip;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import org.springframework.core.annotation.Order;

@Component
@Order(0)
public class DemoData implements CommandLineRunner {
    final PostRepository posts;
    final ObjectMapper mapper;
    final boolean enabled;
    final PostSearchIndexer indexer;
    DemoData(PostRepository posts,ObjectMapper mapper,@Value("${app.seed-demo}") boolean enabled, PostSearchIndexer indexer) {
        this.posts=posts; this.mapper=mapper; this.enabled=enabled; this.indexer=indexer;
    }
    @Override @Transactional
    public void run(String... args) throws Exception {
        if(!enabled || posts.count()!=0) return;
        try(var stream=new ClassPathResource("demo-posts.json").getInputStream()) {
            List<TravelPost> samples=mapper.readValue(stream,new TypeReference<>() {});
            samples.forEach(post -> post.id=null);
            posts.saveAllAndFlush(samples);
            samples.forEach(indexer::replace);
        }
    }
}
