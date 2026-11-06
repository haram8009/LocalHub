package com.enjoytrip;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
@Service
public class PostQueryService {
    final PostRepository posts;
    PostQueryService(PostRepository posts) { this.posts = posts; }
    static boolean hasPlaceFilter(Map<String,String> params) {
        return List.of("placeId","placeName","placeAddress","latitude","longitude").stream().anyMatch(k -> !params.getOrDefault(k, "").isBlank());
    }
    @Transactional(readOnly=true)
    public Map<String,Object> list(Map<String,String> params) {
        int page = integer(params.getOrDefault("page","1"));
        int size = integer(params.getOrDefault("size","10"));
        if (page<1 || size<1 || size>100) throw new ApiException(422,"페이지는 1 이상, 크기는 1~100이어야 합니다.");
        String keyword = params.getOrDefault("query", "").trim().toLowerCase(Locale.ROOT);
        Specification<TravelPost> spec = (root,query,cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            for (String field : List.of("category","authorId")) {
                if (!params.getOrDefault(field, "").isBlank()) predicates.add(cb.equal(root.get(field), params.get(field)));
            }
            if (!keyword.isBlank()) {
                String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.like(cb.lower(cb.concat(cb.concat(root.get("title"), " "), root.get("content"))), "%"+escaped+"%", '\\'));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        boolean placeFilter = hasPlaceFilter(params);
        if (placeFilter) {
            // Fuzzy name/distance matching keeps the existing mock's semantics.
            // DB handles category/author/keyword first; location matching is in Java for this small project.
            var filtered = posts.findAll(spec, Sort.by(Sort.Direction.DESC,"createdAt","id")).stream()
                .filter(p -> p.locations.stream().anyMatch(l -> matches(l,params))).toList();
            long start = (long)(page-1)*size;
            int from = (int)Math.min(start,filtered.size());
            return result(filtered.subList(from,Math.min(from+size,filtered.size())),page,size,filtered.size());
        }
        var result = posts.findAll(spec,PageRequest.of(page-1,size,Sort.by(Sort.Direction.DESC,"createdAt","id")));
        return result(result.getContent(),page,size,result.getTotalElements());
    }
    static Map<String,Object> result(List<TravelPost> items,int page,int size,long total) {
        return Map.of("items",items,"page",page,"size",size,"total",total,"totalPages",(total+size-1)/size);
    }
    static int integer(String value) {
        try { return Integer.parseInt(value); } catch(NumberFormatException e) { throw new ApiException(422,"페이지 형식이 올바르지 않습니다."); }
    }
    static String text(String s) { return s==null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}\\p{S}]",""); }
    static Double coordinate(String value) {
        if(value == null || value.isBlank()) return null;
        try { double n=Double.parseDouble(value); return Double.isFinite(n) ? n : null; }
        catch(NumberFormatException e) { throw new ApiException(422,"좌표 형식이 올바르지 않습니다."); }
    }
    static boolean matches(PostLocation l,Map<String,String> p) {
        if (!p.getOrDefault("placeId","").isBlank() && p.get("placeId").equals(l.contentId)) return true;
        Double lat=coordinate(p.get("latitude")), lon=coordinate(p.get("longitude"));
        double distance=Double.POSITIVE_INFINITY;
        if(lat!=null && lon!=null && l.latitude!=null && l.longitude!=null) {
            double a=Math.pow(Math.sin(Math.toRadians(lat-l.latitude)/2),2)+Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(l.latitude))*Math.pow(Math.sin(Math.toRadians(lon-l.longitude)/2),2);
            distance=2*6371000*Math.asin(Math.sqrt(Math.min(1,a)));
        }
        if(distance<=50) return true;
        String name=text(p.get("placeName")), address=text(p.get("placeAddress"));
        return !name.isBlank() && name.equals(text(l.name)) && (address.isBlank() || text(l.address).isBlank() || address.equals(text(l.address)) || distance<=1000);
    }
}
