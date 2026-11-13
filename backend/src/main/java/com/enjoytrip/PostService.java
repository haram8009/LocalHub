package com.enjoytrip;

import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class PostService {
    final PostRepository posts;
    final AuthService auth;
    final PostSearchIndexer indexer;
    PostService(PostRepository posts, AuthService auth, PostSearchIndexer indexer) {
        this.posts=posts; this.auth=auth; this.indexer=indexer;
    }
    TravelPost find(long id) { return posts.findLockedById(id).orElseThrow(() -> new ApiException(404,"게시글을 찾을 수 없습니다.")); }
    void own(TravelPost post,HttpSession session) {
        if(!post.authorId.equals(auth.require(session).id)) throw new ApiException(403,"작성자만 수정·삭제할 수 있습니다.");
    }
    void apply(TravelPost post,PostInput input) {
        post.category=input.category(); post.title=input.title().trim(); post.content=input.content().trim();
        post.locations.clear();
        for(int i=0;i<input.locations().size();i++) {
            var location=input.locations().get(i); location.visitOrder=i+1; post.locations.add(location);
        }
        post.updatedAt=Instant.now();
    }
    public TravelPost detail(long id) { var post=find(id); posts.incrementViews(id); post.viewCount++; return post; }

    public TravelPost create(PostInput input,HttpSession session) {
        var member=auth.requireForUpdate(session); var post=new TravelPost();
        post.authorId=member.id; post.authorName=member.name; apply(post,input); posts.saveAndFlush(post); indexer.replace(post); return post;
    }
    public TravelPost update(long id,PostInput input,HttpSession session) {
        auth.requireForUpdate(session);
        var post=find(id); own(post,session); apply(post,input); posts.saveAndFlush(post); indexer.replace(post); return post;
    }
    public Map<String,String> delete(long id,HttpSession session) {
        auth.requireForUpdate(session);
        var post=find(id); own(post,session); indexer.remove(id); posts.delete(post); return Map.of("message","게시글이 삭제되었습니다.");
    }
}
