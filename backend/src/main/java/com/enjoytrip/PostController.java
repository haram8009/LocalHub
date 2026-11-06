package com.enjoytrip;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/posts")
@Transactional
public class PostController {
    final PostRepository posts;
    final AuthService auth;
    final PostQueryService query;
    final PostSearchService search;
    final PostSearchIndexer indexer;
    PostController(PostRepository posts, AuthService auth, PostQueryService query, PostSearchService search, PostSearchIndexer indexer) {
        this.posts=posts; this.auth=auth; this.query=query; this.search=search; this.indexer=indexer;
    }
    record Input(@NotBlank @Pattern(regexp="COURSE|REVIEW|TIP|RECOMMENDATION") String category,
                 @NotBlank @Size(max=200) String title, @NotBlank @Size(max=20000) String content,
                 @NotNull @Size(max=32) List<@NotNull @Valid PostLocation> locations) {}
    @GetMapping @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    Map<String,Object> list(@RequestParam Map<String,String> params) {
        return params.getOrDefault("query", "").isBlank() ? query.list(params) : search.search(params);
    }
    TravelPost find(long id) { return posts.findLockedById(id).orElseThrow(() -> new ApiException(404,"게시글을 찾을 수 없습니다.")); }
    void own(TravelPost post,HttpSession session) {
        if(!post.authorId.equals(auth.require(session).id)) throw new ApiException(403,"작성자만 수정·삭제할 수 있습니다.");
    }
    void apply(TravelPost post,Input input) {
        post.category=input.category(); post.title=input.title().trim(); post.content=input.content().trim();
        post.locations.clear();
        for(int i=0;i<input.locations().size();i++) {
            var location=input.locations().get(i); location.visitOrder=i+1; post.locations.add(location);
        }
        post.updatedAt=Instant.now();
    }
    @GetMapping("/{id}") TravelPost detail(@PathVariable long id) { var post=find(id); post.viewCount++; return post; }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    TravelPost create(@Valid @RequestBody Input input,HttpSession session) {
        var member=auth.requireForUpdate(session); var post=new TravelPost();
        post.authorId=member.id; post.authorName=member.name; apply(post,input); posts.saveAndFlush(post); indexer.replace(post); return post;
    }
    @PutMapping("/{id}") TravelPost update(@PathVariable long id,@Valid @RequestBody Input input,HttpSession session) {
        var post=find(id); own(post,session); apply(post,input); indexer.replace(post); return post;
    }
    @DeleteMapping("/{id}") Map<String,String> delete(@PathVariable long id,HttpSession session) {
        var post=find(id); own(post,session); indexer.remove(id); posts.delete(post); return Map.of("message","게시글이 삭제되었습니다.");
    }
}
