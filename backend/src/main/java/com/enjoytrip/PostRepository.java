package com.enjoytrip;
import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
@Repository
@Transactional(readOnly=true)
public class PostRepository {
 private final PostMapper mapper;
 public PostRepository(PostMapper mapper) { this.mapper=mapper; }
 private List<TravelPost> hydrate(List<TravelPost> posts) {
  if(posts.isEmpty()) return posts;
  var byId=new HashMap<Long,TravelPost>(); posts.forEach(p->byId.put(p.id,p));
  for(var row:mapper.findLocationSnapshots(byId.keySet())) byId.get(row.postId).locations.add(row.location);
  return posts;
 }
 public Optional<TravelPost> findById(long id) { var p=mapper.findById(id); return p==null?Optional.empty():Optional.of(hydrate(List.of(p)).get(0)); }
 public Optional<TravelPost> findLockedById(long id) { var p=mapper.findLockedById(id); return p==null?Optional.empty():Optional.of(hydrate(List.of(p)).get(0)); }
 public boolean existsById(long id) { return mapper.existsById(id); }
 public long count() { return mapper.count(); }
 @Transactional public void incrementViews(long id) { mapper.incrementViews(id); }
 @Transactional public TravelPost saveAndFlush(TravelPost p) {
  if(p.id==null) mapper.insert(p); else mapper.update(p);
  mapper.deleteLocations(p.id);
  for(int i=0;i<p.locations.size();i++) mapper.insertLocation(p.id,i,p.locations.get(i));
  return p;
 }
 @Transactional public void saveAllAndFlush(List<TravelPost> posts) { posts.forEach(this::saveAndFlush); }
 @Transactional public void deleteById(long id) { mapper.deleteLocations(id); mapper.deleteById(id); }
 @Transactional public void delete(TravelPost p) { deleteById(p.id); }
 @Transactional public void deleteAll() {
  // Explicit child deletion also supports the legacy schema without ON DELETE CASCADE.
  for(var p:mapper.list(Map.of())) deleteById(p.id);
 }
 @Transactional public void deleteByAuthorId(String author) { mapper.findIdsByAuthor(author).forEach(this::deleteById); }
 public List<TravelPost> list(Map<String,Object> params) { return hydrate(mapper.list(params)); }
 public long countFiltered(Map<String,Object> params) { return mapper.countFiltered(params); }
 public List<TravelPost> scan(long offset,int limit) { return hydrate(mapper.scan(offset,limit)); }
 public List<TravelPost> findWithLocationsByIds(Collection<Long> ids) { return ids.isEmpty()?List.of():hydrate(mapper.findWithLocationsByIds(ids)); }
 public List<LocationSnapshot> findLocationSnapshots(Collection<Long> ids) { return ids.isEmpty()?List.of():mapper.findLocationSnapshots(ids); }
 public List<Long> findCourseCandidateIds(long baseId,Collection<String> placeIds) { return placeIds.isEmpty()?List.of():mapper.findCourseCandidateIds(baseId,placeIds); }
 public List<CoursePoint> findCoursePoints(Collection<Long> ids) { return ids.isEmpty()?List.of():mapper.findCoursePoints(ids); }
 public List<CourseSummary> findCourseSummaries(Collection<Long> ids) { return ids.isEmpty()?List.of():mapper.findCourseSummaries(ids); }
 public List<CourseTimestamp> findCourseTimestamps(Collection<Long> ids) { return ids.isEmpty()?List.of():mapper.findCourseTimestamps(ids); }
 public List<SearchMetadata> findSearchMetadata(Collection<Long> ids,String category,String author) { return ids.isEmpty()?List.of():mapper.findSearchMetadata(ids,category,author); }
 public List<NearbyPoint> findNearbyPoints(double a,double b,double c,double d) { return mapper.findNearbyPoints(a,b,c,d); }
 public static class CoursePoint {
public Long postId; public Long getPostId() { return postId; }
public String contentId; public String getContentId() { return contentId; }
public Integer visitOrder; public Integer getVisitOrder() { return visitOrder; }
}
public static class CourseSummary {
public Long id; public Long getId() { return id; }
public String title; public String getTitle() { return title; }
public String content; public String getContent() { return content; }
public String category; public String getCategory() { return category; }
public String authorId; public String getAuthorId() { return authorId; }
public String authorName; public String getAuthorName() { return authorName; }
public long viewCount; public long getViewCount() { return viewCount; }
public Instant createdAt; public Instant getCreatedAt() { return createdAt; }
public Instant updatedAt; public Instant getUpdatedAt() { return updatedAt; }
public int locationCount; public int getLocationCount() { return locationCount; }
}
public static class CourseTimestamp {
public Long id; public Long getId() { return id; }
public Instant createdAt; public Instant getCreatedAt() { return createdAt; }
}
public static class SearchMetadata {
public Long id; public Long getId() { return id; }
public Instant createdAt; public Instant getCreatedAt() { return createdAt; }
}
public static class LocationSnapshot {
public Long postId; public Long getPostId() { return postId; }
public PostLocation location; public PostLocation getLocation() { return location; }
}
public static class NearbyPoint {
public Long postId; public Long getPostId() { return postId; }
public Instant createdAt; public Instant getCreatedAt() { return createdAt; }
public String contentId; public String getContentId() { return contentId; }
public String contentTypeId; public String getContentTypeId() { return contentTypeId; }
public String name; public String getName() { return name; }
public String address; public String getAddress() { return address; }
public String category; public String getCategory() { return category; }
public String region; public String getRegion() { return region; }
public Double latitude; public Double getLatitude() { return latitude; }
public Double longitude; public Double getLongitude() { return longitude; }
public String thumbnailImageUrl; public String getThumbnailImageUrl() { return thumbnailImageUrl; }
}
}
