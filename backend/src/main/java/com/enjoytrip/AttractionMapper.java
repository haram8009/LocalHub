package com.enjoytrip;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface AttractionMapper {
 List<Map<String,Object>> codes(@Param("sido") Integer sido);
 long count(Map<String,Object> params);
 List<Map<String,Object>> search(Map<String,Object> params);
}
