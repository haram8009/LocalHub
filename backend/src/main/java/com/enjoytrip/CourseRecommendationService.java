package com.enjoytrip;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CourseRecommendationService {
    final PostRepository posts;
    final CourseSimilarityCalculator calculator;
    CourseRecommendationService(PostRepository posts, CourseSimilarityCalculator calculator) {
        this.posts = posts; this.calculator = calculator;
    }
    record Ranked(long id, Instant createdAt, CourseSimilarityCalculator.Similarity similarity) {}

    // All multi-query reads observe the same snapshot, including edits/deletes.
    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public Map<String, Object> recommend(long postId, int limit) {
        if (limit < 1 || limit > 10) throw new ApiException(400, "추천 개수는 1~10이어야 합니다.");
        if (!posts.existsById(postId)) throw new ApiException(404, "게시글을 찾을 수 없습니다.");
        var base = posts.findCoursePoints(List.of(postId)).stream()
            .map(PostRepository.CoursePoint::getContentId).filter(Objects::nonNull).filter(s -> !s.isBlank()).toList();
        if (base.isEmpty()) return Map.of("items", List.of());
        // Same external contentId means the same place. No fuzzy name/coordinate merge.
        // demo-* identifiers cannot equal real TourAPI identifiers.
        var candidateIds = posts.findCourseCandidateIds(postId, base);
        if (candidateIds.isEmpty()) return Map.of("items", List.of());
        Map<Long, List<String>> routes = new HashMap<>();
        for (var point : posts.findCoursePoints(candidateIds))
            routes.computeIfAbsent(point.getPostId(), key -> new ArrayList<>()).add(point.getContentId());
        var dates = posts.findCourseTimestamps(candidateIds).stream().collect(Collectors.toMap(
            PostRepository.CourseTimestamp::getId, PostRepository.CourseTimestamp::getCreatedAt));
        var ranked = new ArrayList<Ranked>();
        routes.forEach((id, route) -> {
            var similarity = calculator.calculate(base, route);
            if (similarity.commonPlaceCount() > 0) ranked.add(new Ranked(id, dates.get(id), similarity));
        });
        ranked.sort(Comparator.<Ranked>comparingDouble(r -> r.similarity().score()).reversed()
            .thenComparing(Ranked::createdAt, Comparator.reverseOrder())
            .thenComparing(Ranked::id, Comparator.reverseOrder()));
        var top = ranked.stream().limit(limit).toList();
        if (top.isEmpty()) return Map.of("items", List.of());
        var summaries = posts.findCourseSummaries(top.stream().map(Ranked::id).toList()).stream()
            .collect(Collectors.toMap(PostRepository.CourseSummary::getId, Function.identity()));
        var items = new ArrayList<Map<String, Object>>();
        for (var rank : top) {
            var p = summaries.get(rank.id()); var s = rank.similarity();
            var item = new LinkedHashMap<String, Object>();
            item.put("id", p.getId()); item.put("title", p.getTitle()); item.put("content", p.getContent());
            item.put("category", p.getCategory()); item.put("categoryLabel", TravelPost.categoryLabel(p.getCategory()));
            item.put("authorId", p.getAuthorId()); item.put("authorName", p.getAuthorName());
            item.put("viewCount", p.getViewCount()); item.put("createdAt", p.getCreatedAt()); item.put("updatedAt", p.getUpdatedAt());
            item.put("locationCount", p.getLocationCount()); item.put("score", s.score());
            item.put("commonPlaceCount", s.commonPlaceCount()); item.put("lcsLength", s.lcsLength());
            item.put("placeSimilarity", s.placeSimilarity()); item.put("orderSimilarity", s.orderSimilarity());
            items.add(item);
        }
        return Map.of("items", items);
    }
}
