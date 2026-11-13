package com.enjoytrip;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PostSearchService {
    final PostSearchIndexRepository index;
    final SearchTextNormalizer normalizer;
    final PostingSetOperations operations;
    final PostRepository posts;
    final PostQueryService latest;

    PostSearchService(PostSearchIndexRepository index, SearchTextNormalizer normalizer, PostingSetOperations operations,
                      PostRepository posts, PostQueryService latest) {
        this.index = index; this.normalizer = normalizer; this.operations = operations;
        this.posts = posts; this.latest = latest;
    }
    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public Map<String, Object> search(Map<String, String> params) {
        String text = params.getOrDefault("query", "");
        int page = positive(params.getOrDefault("page", "1")), size = positive(params.getOrDefault("size", "10"));
        if (size > 50 || text.length() > 100)
            throw new ApiException(400, "검색어는 100자 이하, 페이지 크기는 1~50이어야 합니다.");
        List<String> queries = normalizer.tokens(text);
        if (queries.size() > 10) throw new ApiException(400, "검색 단어는 최대 10개입니다.");
        if (queries.isEmpty()) {
            var fallback = new HashMap<>(params); fallback.remove("query");
            return latest.list(fallback);
        }
        var version = index.version();
        if (!SearchIndexState.VERSION.equals(version))
            throw new ApiException(503, "검색 인덱스를 준비 중입니다. 잠시 후 다시 시도해 주세요.");
        Map<String, Map<Long, Integer>> hits = new LinkedHashMap<>();
        queries.forEach(query -> hits.put(query, new HashMap<>()));
        // Fetch all matching dictionary postings once. One term may match several query tokens.
        for (var row : index.findContaining(queries)) for (String query : queries) {
            if (row.term().contains(query)) {
                int flags = (row.titleHit() ? 2 : 0) | (row.bodyHit() ? 1 : 0);
                // Saturating flags prevent score inflation from 부산 + 부산에서 for one query 부산.
                hits.get(query).merge(row.postId(), flags, (left, right) -> left | right);
            }
        }
        Set<Long> candidates = operations.intersect(hits.values().stream().map(Map::keySet).toList());
        if (candidates.isEmpty()) return result(List.of(), page, size, 0);
        var metadata = posts.findSearchMetadata(candidates, params.getOrDefault("category", ""), params.getOrDefault("authorId", ""));
        if (metadata.isEmpty()) return result(List.of(), page, size, 0);
        Set<Long> allowed = metadata.stream().map(PostRepository.SearchMetadata::getId).collect(Collectors.toSet());
        if (PostQueryService.hasPlaceFilter(params)) {
            Set<Long> located = posts.findLocationSnapshots(allowed).stream()
                .filter(row -> PostQueryService.matches(row.getLocation(), params))
                .map(PostRepository.LocationSnapshot::getPostId).collect(Collectors.toSet());
            allowed.retainAll(located);
        }
        Map<Long, Integer> scores = new HashMap<>();
        for (Long id : allowed) {
            int score = 0;
            for (var termHits : hits.values()) score += termHits.getOrDefault(id, 0);
            scores.put(id, score);
        }
        var dates = metadata.stream().collect(Collectors.toMap(PostRepository.SearchMetadata::getId, PostRepository.SearchMetadata::getCreatedAt));
        var ranked = allowed.stream().sorted(Comparator.<Long>comparingInt(scores::get).reversed()
            .thenComparing(dates::get, Comparator.reverseOrder()).thenComparing(Comparator.reverseOrder())).toList();
        long start = (long)(page - 1) * size;
        if (start >= ranked.size()) return result(List.of(), page, size, ranked.size());
        var pageIds = ranked.subList((int)start, (int)Math.min(start + size, ranked.size()));
        var entities = posts.findWithLocationsByIds(pageIds).stream().collect(Collectors.toMap(p -> p.id, p -> p));
        var items = new ArrayList<Map<String, Object>>();
        for (Long id : pageIds) {
            var p = entities.get(id); var item = new LinkedHashMap<String, Object>();
            item.put("id", p.id); item.put("category", p.category); item.put("categoryLabel", p.getCategoryLabel());
            item.put("title", p.title); item.put("content", p.content); item.put("authorId", p.authorId); item.put("authorName", p.authorName);
            item.put("viewCount", p.viewCount); item.put("createdAt", p.createdAt); item.put("updatedAt", p.updatedAt);
            item.put("locationCount", p.locations.size()); item.put("locations", p.locations); item.put("relevanceScore", scores.get(id));
            items.add(item);
        }
        return result(items, page, size, ranked.size());
    }
    static int positive(String value) {
        try { int n = Integer.parseInt(value); if (n > 0) return n; }
        catch (NumberFormatException ignored) {}
        throw new ApiException(400, "페이지와 크기는 양의 정수여야 합니다.");
    }
    static Map<String, Object> result(List<?> items, int page, int size, long total) {
        return Map.of("items", items, "page", page, "size", size, "total", total, "totalPages", (total + size - 1) / size);
    }
}
