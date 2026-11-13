package com.enjoytrip;

import java.time.Instant;
import java.util.*;

public class TravelPost {
    public Long id;
    public String category;
    public String title;

    public String content;
    public String authorId;
    public String authorName;
    public long viewCount;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = createdAt;

    public List<PostLocation> locations = new ArrayList<>();
    protected TravelPost() {}
    public String getCategoryLabel() {
        return categoryLabel(category);
    }
    public static String categoryLabel(String category) {
        return switch(category) { case "REVIEW" -> "여행 후기"; case "TIP" -> "여행 팁"; case "RECOMMENDATION" -> "여행지 추천"; default -> "여행 코스"; };
    }
}
