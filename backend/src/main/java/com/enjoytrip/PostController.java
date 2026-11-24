package com.enjoytrip;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/posts")
public class PostController {
 private final PostService service;
 private final PostQueryService query;
 private final PostSearchService search;
 public PostController(PostService service,PostQueryService query,PostSearchService search) { this.service=service; this.query=query; this.search=search; }
 @GetMapping public Map<String,Object> list(@RequestParam Map<String,String> params) { return params.getOrDefault("query", "").isBlank()?query.list(params):search.search(params); }
 @GetMapping("/{id}") public TravelPost detail(@PathVariable long id) { return service.detail(id); }
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public TravelPost create(@Valid @RequestBody PostInput input,@AuthenticationPrincipal AuthPrincipal principal) { return service.create(input,principal); }
 @PutMapping("/{id}") public TravelPost update(@PathVariable long id,@Valid @RequestBody PostInput input,@AuthenticationPrincipal AuthPrincipal principal) { return service.update(id,input,principal); }
 @DeleteMapping("/{id}") public Map<String,String> delete(@PathVariable long id,@AuthenticationPrincipal AuthPrincipal principal) { return service.delete(id,principal); }
}
