package com.enjoytrip;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** JDBC DAO for the supplied SSAFY tourism schema. All user values are bound parameters. */
@Repository
public class AttractionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AttractionRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<Map<String,Object>> codes(Integer sido) {
        String sql = sido == null
            ? "select sido_code as code, sido_name as name from sidos order by sido_code"
            : "select gugun_code as code, gugun_name as name from guguns where sido_code=:sido order by gugun_code";
        return jdbc.query(sql, sido == null ? Map.of() : Map.of("sido",sido),
            (rs,n) -> Map.of("code", rs.getString("code"), "name", rs.getString("name")));
    }

    record Result(List<Map<String,Object>> items, long total) {}
    Result search(Map<String,Object> values, int page, int size, boolean nearby, boolean distanceOrder) {
        StringBuilder where = new StringBuilder(" where 1=1");
        for (String field : List.of("area_code","si_gun_gu_code","content_type_id","content_id")) {
            if(values.containsKey(field)) where.append(" and ").append(field).append("=:").append(field);
        }
        if(values.containsKey("keyword")) where.append(" and title like :keyword escape '!'");
        String distance = "(6371000 * acos(least(1.0,greatest(-1.0,sin(radians(:y))*sin(radians(latitude))"
            + "+cos(radians(:y))*cos(radians(latitude))*cos(radians(longitude)-radians(:x))))))";
        if(nearby) where.append(" and latitude is not null and longitude is not null and ").append(distance).append(" <= :radius");
        long total = jdbc.queryForObject("select count(*) from attractions"+where,values,Long.class);
        var params = new HashMap<String,Object>(values);
        params.put("limit",size); params.put("offset",(long)(page-1)*size);
        String sql = "select *, " + (nearby ? distance : "null") + " as distance_meters from attractions" + where
            + (nearby && distanceOrder ? " order by distance_meters,no" : " order by title,no") + " limit :limit offset :offset";
        var items = jdbc.query(sql,params,(rs,n) -> {
            var item = new LinkedHashMap<String,Object>();
            String[][] fields = {{"contentid","content_id"},{"title","title"},{"contenttypeid","content_type_id"},
                {"areacode","area_code"},{"sigungucode","si_gun_gu_code"},{"firstimage","first_image1"},
                {"firstimage2","first_image2"},{"mapx","longitude"},{"mapy","latitude"},{"mlevel","map_level"},
                {"tel","tel"},{"addr1","addr1"},{"addr2","addr2"},{"homepage","homepage"},{"overview","overview"},
                {"dist","distance_meters"}};
            for(var field: fields) item.put(field[0],rs.getObject(field[1]));
            return (Map<String,Object>)item;
        });
        return new Result(items,total);
    }
}
