package com.enjoytrip;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class NearbyPostService {
    private static final double EARTH_RADIUS_KM = 6371;
    private static final double NEARBY_RADIUS_KM = 5;
    // Keep the same coordinate bounds as frontend/src/utils/geo.js.
    private static final double MIN_LATITUDE = 32, MAX_LATITUDE = 40;
    private static final double MIN_LONGITUDE = 123, MAX_LONGITUDE = 133;
    final PostRepository posts;
    NearbyPostService(PostRepository posts) { this.posts = posts; }

    record Ranked(long id, Instant createdAt, double distanceKm, PostRepository.NearbyPoint location) {}

    // InnoDB REPEATABLE READ: 첫 읽기 시점의 스냅샷을 잠금 없이 끝까지 읽습니다(쓰기를 막지 않음).
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> nearby(double latitude, double longitude, int limit) {
        if (!validCoordinate(latitude, longitude))
            throw new ApiException(400, "국내 위치의 올바른 위도와 경도를 입력해 주세요.");
        if (limit < 1 || limit > 10) throw new ApiException(400, "추천 개수는 1~10이어야 합니다.");

        double angularRadius = NEARBY_RADIUS_KM / EARTH_RADIUS_KM;
        double latitudeDelta = Math.toDegrees(angularRadius);
        double longitudeDelta = Math.toDegrees(Math.asin(Math.sin(angularRadius) / Math.cos(Math.toRadians(latitude))));
        // 상자(좌표 인덱스 범위)로 후보를 줄이고, 반경 판정·글별 최근접 선택·정렬·상위 N 은 DB가 합니다.
        // 예전에는 상자 안 장소 전체(서울 기준 2만여 행, 본문 열 포함)를 받아 자바에서 골랐습니다.
        var points = posts.findNearestPoints(latitude, longitude, NEARBY_RADIUS_KM,
            Math.max(MIN_LATITUDE, latitude - latitudeDelta), Math.min(MAX_LATITUDE, latitude + latitudeDelta),
            Math.max(MIN_LONGITUDE, longitude - longitudeDelta), Math.min(MAX_LONGITUDE, longitude + longitudeDelta), limit);
        var top = points.stream()
            .map(point -> new Ranked(point.getPostId(), point.getCreatedAt(),
                distanceKm(latitude, longitude, point.getLatitude(), point.getLongitude()), point))
            .toList();
        if (top.isEmpty()) return Map.of("items", List.of());

        var summaries = posts.findCourseSummaries(top.stream().map(Ranked::id).toList()).stream()
            .collect(Collectors.toMap(PostRepository.CourseSummary::getId, Function.identity()));
        var items = new ArrayList<Map<String, Object>>();
        for (var ranked : top) {
            var p = summaries.get(ranked.id());
            var item = new LinkedHashMap<String, Object>();
            item.put("id", p.getId()); item.put("title", p.getTitle()); item.put("content", p.getContent());
            item.put("category", p.getCategory()); item.put("categoryLabel", TravelPost.categoryLabel(p.getCategory()));
            item.put("authorId", p.getAuthorId()); item.put("authorName", p.getAuthorName());
            item.put("viewCount", p.getViewCount()); item.put("createdAt", p.getCreatedAt()); item.put("updatedAt", p.getUpdatedAt());
            item.put("locationCount", p.getLocationCount()); item.put("distanceKm", ranked.distanceKm());
            item.put("nearestLocation", locationSummary(ranked.location()));
            items.add(item);
        }
        return Map.of("items", items);
    }

    private static boolean validCoordinate(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
            && latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE
            && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    private static double distanceKm(double latitude, double longitude, double targetLatitude, double targetLongitude) {
        double a = Math.pow(Math.sin(Math.toRadians(targetLatitude - latitude) / 2), 2)
            + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(targetLatitude))
            * Math.pow(Math.sin(Math.toRadians(targetLongitude - longitude) / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.min(1, a)));
    }

    private static Map<String, Object> locationSummary(PostRepository.NearbyPoint location) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", location.getContentId()); result.put("contentId", location.getContentId());
        result.put("contentTypeId", location.getContentTypeId()); result.put("name", location.getName());
        result.put("address", location.getAddress()); result.put("region", location.getRegion()); result.put("category", location.getCategory());
        result.put("latitude", location.getLatitude()); result.put("longitude", location.getLongitude());
        result.put("thumbnailImageUrl", location.getThumbnailImageUrl());
        return result;
    }
}
