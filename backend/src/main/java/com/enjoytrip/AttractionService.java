package com.enjoytrip;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Keeps the existing tour response contract while reading the local tourism database. */
@Service
@Transactional(readOnly=true)
public class AttractionService {
    private final AttractionRepository repository;
    public AttractionService(AttractionRepository repository) { this.repository=repository; }

    public Map<String,Object> request(String operation, Map<String,String> p) {
        if(!ExternalApiController.OPERATIONS.contains(operation)) throw new ApiException(400,"지원하지 않는 관광정보 요청입니다.");
        int page=integer(p,"pageNo",1,1,1000000), size=integer(p,"numOfRows",20,1,1000);
        if(operation.equals("ldongCode2")) {
            var codes=repository.codes(optional(p,"lDongRegnCd"));
            int from=(int)Math.min((long)(page-1)*size,codes.size());
            return envelope(codes.subList(from,Math.min(from+size,codes.size())),codes.size(),page,size);
        }
        for(String key:List.of("lclsSystm1","lclsSystm2","lclsSystm3")) {
            if(!p.getOrDefault(key,"").isBlank()) throw new ApiException(422,"제공 DB는 세부 분류를 지원하지 않습니다. 관광 유형을 선택해 주세요.");
        }
        var values=new HashMap<String,Object>();
        boolean detail=operation.startsWith("detail");
        if(detail) {
            values.put("content_id",integer(p,"contentId",null,1,Integer.MAX_VALUE));
        } else {
            put(values,"area_code",optional(p,"lDongRegnCd"));
            put(values,"si_gun_gu_code",optional(p,"lDongSignguCd"));
            put(values,"content_type_id",optional(p,"contentTypeId"));
            if(values.containsKey("si_gun_gu_code")&&!values.containsKey("area_code")) throw new ApiException(422,"시도를 먼저 선택해 주세요.");
            if(operation.equals("searchKeyword2")) {
                String keyword=p.getOrDefault("keyword","").trim();
                if(keyword.isEmpty()||keyword.length()>500) throw new ApiException(422,"검색어는 1~500자로 입력해 주세요.");
                values.put("keyword","%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
            }
        }
        boolean nearby=operation.equals("locationBasedList2");
        if(nearby) {
            values.put("x",decimal(p,"mapX",-180,180)); values.put("y",decimal(p,"mapY",-90,90));
            values.put("radius",decimal(p,"radius",1,20000));
        }
        var result=repository.search(values,detail?1:page,detail?1:size,nearby,"E".equals(p.get("arrange")));
        if(detail && result.items().isEmpty()) throw new ApiException(404,"관광지를 찾을 수 없습니다.");
        if(operation.equals("detailImage2")) {
            List<Map<String,Object>> images=new ArrayList<>();
            for(String key:List.of("firstimage","firstimage2")) {
                Object url=result.items().get(0).get(key);
                if(url!=null&&!url.toString().isBlank()&&images.stream().noneMatch(i->url.equals(i.get("originimgurl"))))
                    images.add(Map.of("originimgurl",url,"smallimageurl",url));
            }
            return envelope(images,images.size(),1,size);
        }
        if(operation.equals("detailInfo2")||operation.equals("detailIntro2")) return envelope(List.of(),0,1,size);
        return envelope(result.items(),result.total(),page,size);
    }
    private static void put(Map<String,Object> target,String key,Object value) { if(value!=null) target.put(key,value); }
    private static Integer optional(Map<String,String> p,String key) {
        return p.getOrDefault(key,"").isBlank()?null:integer(p,key,null,1,Integer.MAX_VALUE);
    }
    private static int integer(Map<String,String> p,String key,Integer fallback,int min,int max) {
        try {
            String raw=p.get(key);
            int value=raw==null||raw.isBlank()?Objects.requireNonNull(fallback):Integer.parseInt(raw);
            if(value<min||value>max) throw new IllegalArgumentException();
            return value;
        } catch(RuntimeException e) { throw new ApiException(422,key+" 값이 올바르지 않습니다."); }
    }
    private static double decimal(Map<String,String> p,String key,double min,double max) {
        try {
            double value=Double.parseDouble(p.get(key));
            if(!Double.isFinite(value)||value<min||value>max) throw new IllegalArgumentException();
            return value;
        } catch(RuntimeException e) { throw new ApiException(422,key+" 값이 올바르지 않습니다."); }
    }
    private static Map<String,Object> envelope(List<Map<String,Object>> items,long total,int page,int size) {
        return Map.of("response",Map.of("header",Map.of("resultCode","0000","resultMsg","OK"),
            "body",Map.of("items",Map.of("item",items),"totalCount",total,"pageNo",page,"numOfRows",size)));
    }
}
