package com.enjoytrip;
import jakarta.servlet.http.HttpSession;
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
 public TravelPost create(@Valid @RequestBody PostInput input,HttpSession session) { return service.create(input,session); }
 @PutMapping("/{id}") public TravelPost update(@PathVariable long id,@Valid @RequestBody PostInput input,HttpSession session) { return service.update(id,input,session); }
 @DeleteMapping("/{id}") public Map<String,String> delete(@PathVariable long id,HttpSession session) { return service.delete(id,session); }
}
