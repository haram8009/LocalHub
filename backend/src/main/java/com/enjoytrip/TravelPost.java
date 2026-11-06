package com.enjoytrip;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="travel_posts", indexes={@Index(name="idx_post_author",columnList="authorId"), @Index(name="idx_post_created",columnList="createdAt")})
public class TravelPost {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,length=30) public String category;
    @Column(nullable=false,length=200) public String title;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONG32VARCHAR)
    @Column(nullable=false) public String content;
    @Column(nullable=false,length=20) public String authorId;
    @Column(nullable=false,length=20) public String authorName;
    public long viewCount;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = createdAt;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="post_locations",joinColumns=@JoinColumn(name="post_id"),
        indexes=@Index(name="idx_post_location_coordinates",columnList="latitude,longitude"))
    @OrderColumn(name="visit_index")
    public List<PostLocation> locations = new ArrayList<>();
    protected TravelPost() {}
    public String getCategoryLabel() {
        return categoryLabel(category);
    }
    public static String categoryLabel(String category) {
        return switch(category) { case "REVIEW" -> "여행 후기"; case "TIP" -> "여행 팁"; case "RECOMMENDATION" -> "여행지 추천"; default -> "여행 코스"; };
    }
}
