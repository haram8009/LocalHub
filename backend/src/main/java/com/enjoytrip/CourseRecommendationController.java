package com.enjoytrip;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
public class CourseRecommendationController {
    final CourseRecommendationService service;
    CourseRecommendationController(CourseRecommendationService service) { this.service = service; }
    @GetMapping("/{postId}/similar")
    Map<String, Object> similar(@PathVariable long postId, @RequestParam(defaultValue = "3") int limit) {
        return service.recommend(postId, limit);
    }
}
