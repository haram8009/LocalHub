package com.enjoytrip;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
public class NearbyPostController {
    final NearbyPostService service;
    NearbyPostController(NearbyPostService service) { this.service = service; }

    @GetMapping("/nearby")
    Map<String, Object> nearby(@RequestParam double latitude, @RequestParam double longitude,
                               @RequestParam(defaultValue = "3") int limit) {
        return service.nearby(latitude, longitude, limit);
    }
}
