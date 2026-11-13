package com.enjoytrip;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface PostMapper {
 TravelPost findById(@Param("id") long id);
 TravelPost findLockedById(@Param("id") long id);
 long count();
 boolean existsById(@Param("id") long id);
 int insert(TravelPost post);
 int update(TravelPost post);
 int incrementViews(@Param("id") long id);
 int deleteById(@Param("id") long id);
 int deleteAll();
 List<Long> findIdsByAuthor(@Param("author") String author);
 int deleteLocations(@Param("id") long id);
 int insertLocation(@Param("id") long id,@Param("index") int index,@Param("location") PostLocation location);
 List<PostRepository.LocationSnapshot> findLocationSnapshots(@Param("ids") Collection<Long> ids);
 List<TravelPost> findWithLocationsByIds(@Param("ids") Collection<Long> ids);
 List<TravelPost> list(Map<String,Object> params);
 long countFiltered(Map<String,Object> params);
 List<TravelPost> scan(@Param("offset") long offset,@Param("limit") int limit);
 List<Long> findCourseCandidateIds(@Param("baseId") long baseId,@Param("placeIds") Collection<String> placeIds);
 List<PostRepository.CoursePoint> findCoursePoints(@Param("ids") Collection<Long> ids);
 List<PostRepository.CourseSummary> findCourseSummaries(@Param("ids") Collection<Long> ids);
 List<PostRepository.CourseTimestamp> findCourseTimestamps(@Param("ids") Collection<Long> ids);
 List<PostRepository.SearchMetadata> findSearchMetadata(@Param("ids") Collection<Long> ids,@Param("category") String category,@Param("author") String author);
 List<PostRepository.NearbyPoint> findNearbyPoints(@Param("minLatitude") double minLatitude,@Param("maxLatitude") double maxLatitude,@Param("minLongitude") double minLongitude,@Param("maxLongitude") double maxLongitude);
}
