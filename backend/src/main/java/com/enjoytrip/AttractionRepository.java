package com.enjoytrip;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class AttractionRepository {
 private final AttractionMapper mapper;
 public AttractionRepository(AttractionMapper mapper) { this.mapper=mapper; }
 List<Map<String,Object>> codes(Integer sido) { return mapper.codes(sido); }
 record Result(List<Map<String,Object>> items,long total) {}
 Result search(Map<String,Object> values,int page,int size,boolean nearby,boolean distanceOrder) {
  var params=new HashMap<String,Object>(values);
  params.put("nearby",nearby); params.put("distanceOrder",distanceOrder);
  params.put("limit",size); params.put("offset",(long)(page-1)*size);
  long total=mapper.count(params);
  return new Result(mapper.search(params),total);
 }
}
