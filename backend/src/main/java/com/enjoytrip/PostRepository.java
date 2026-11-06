package com.enjoytrip;

import java.util.*;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<TravelPost,Long>, JpaSpecificationExecutor<TravelPost> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TravelPost p where p.id = :id")
    Optional<TravelPost> findLockedById(@Param("id") Long id);
    void deleteByAuthorId(String authorId);

    interface CoursePoint {
        Long getPostId();
        String getContentId();
        Integer getVisitOrder();
    }
    interface CourseSummary {
        Long getId();
        String getTitle();
        String getContent();
        String getCategory();
        String getAuthorId();
        String getAuthorName();
        long getViewCount();
        Instant getCreatedAt();
        Instant getUpdatedAt();
        int getLocationCount();
    }

    @Query("select distinct p.id from TravelPost p join p.locations l where p.id <> :baseId and l.contentId in :placeIds")
    List<Long> findCourseCandidateIds(@Param("baseId") long baseId, @Param("placeIds") Collection<String> placeIds);

    @Query("select p.id as postId, l.contentId as contentId, l.visitOrder as visitOrder from TravelPost p join p.locations l where p.id in :ids order by p.id, l.visitOrder")
    List<CoursePoint> findCoursePoints(@Param("ids") Collection<Long> ids);

    @Query("select p.id as id, p.title as title, p.content as content, p.category as category, p.authorId as authorId, p.authorName as authorName, p.viewCount as viewCount, p.createdAt as createdAt, p.updatedAt as updatedAt, size(p.locations) as locationCount from TravelPost p where p.id in :ids")
    List<CourseSummary> findCourseSummaries(@Param("ids") Collection<Long> ids);

    interface CourseTimestamp { Long getId(); Instant getCreatedAt(); }
    @Query("select p.id as id, p.createdAt as createdAt from TravelPost p where p.id in :ids")
    List<CourseTimestamp> findCourseTimestamps(@Param("ids") Collection<Long> ids);

    interface SearchMetadata { Long getId(); Instant getCreatedAt(); }
    @Query("select p.id as id, p.createdAt as createdAt from TravelPost p where p.id in :ids and (:category = '' or p.category = :category) and (:author = '' or p.authorId = :author)")
    List<SearchMetadata> findSearchMetadata(@Param("ids") Collection<Long> ids, @Param("category") String category, @Param("author") String author);

    interface LocationSnapshot { Long getPostId(); PostLocation getLocation(); }
    @Query("select p.id as postId, l as location from TravelPost p join p.locations l where p.id in :ids")
    List<LocationSnapshot> findLocationSnapshots(@Param("ids") Collection<Long> ids);

    interface NearbyPoint {
        Long getPostId();
        Instant getCreatedAt();
        String getContentId();
        String getContentTypeId();
        String getName();
        String getAddress();
        String getCategory();
        String getRegion();
        Double getLatitude();
        Double getLongitude();
        String getThumbnailImageUrl();
    }
    @Query("select p.id as postId, p.createdAt as createdAt, l.contentId as contentId, l.contentTypeId as contentTypeId, l.name as name, l.address as address, l.category as category, l.region as region, l.latitude as latitude, l.longitude as longitude, l.thumbnailImageUrl as thumbnailImageUrl from TravelPost p join p.locations l where l.latitude between :minLatitude and :maxLatitude and l.longitude between :minLongitude and :maxLongitude order by p.id, l.visitOrder")
    List<NearbyPoint> findNearbyPoints(@Param("minLatitude") double minLatitude,
                                      @Param("maxLatitude") double maxLatitude,
                                      @Param("minLongitude") double minLongitude,
                                      @Param("maxLongitude") double maxLongitude);

    @Query("select distinct p from TravelPost p left join fetch p.locations where p.id in :ids")
    List<TravelPost> findWithLocationsByIds(@Param("ids") Collection<Long> ids);
}
