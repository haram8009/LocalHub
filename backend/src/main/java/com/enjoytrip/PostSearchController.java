package com.enjoytrip;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/search")
public class PostSearchController {
    final PostSearchService search;
    PostSearchController(PostSearchService search) { this.search = search; }
    @GetMapping Map<String, Object> search(@RequestParam Map<String, String> params) { return search.search(params); }
}
