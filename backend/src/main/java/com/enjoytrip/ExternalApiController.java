package com.enjoytrip;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ExternalApiController {
    private final AttractionService attractions;
    private final String tourSource;
    final RestClient client;
    final String tourKey, kakaoKey;
    static final Set<String> OPERATIONS=Set.of("ldongCode2","areaBasedList2","locationBasedList2","searchKeyword2","detailCommon2","detailIntro2","detailImage2","detailInfo2");
    ExternalApiController(@Value("${app.tour-key}") String tourKey,@Value("${app.kakao-key}") String kakaoKey,
                          @Value("${app.tour-source:api}") String tourSource, AttractionService attractions) {
        this.attractions=attractions; this.tourSource=tourSource;
        this.tourKey=tourKey; this.kakaoKey=kakaoKey;
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5)); factory.setReadTimeout(Duration.ofSeconds(15));
        client=RestClient.builder().requestFactory(factory).build();
    }
    void key(String key) { if(key.isBlank()) throw new ApiException(503,"백엔드의 외부 API 인증키가 설정되지 않았습니다."); }
    @GetMapping("/tour/{operation}") Object tour(@PathVariable String operation,@RequestParam Map<String,String> params) {
        if("database".equals(tourSource)) return attractions.request(operation,params);
        key(tourKey);
        if(!OPERATIONS.contains(operation)) throw new ApiException(400,"지원하지 않는 관광정보 요청입니다.");
        var uri=UriComponentsBuilder.fromUriString("https://apis.data.go.kr/B551011/KorService2/"+operation);
        params.forEach((k,v) -> { if(!Set.of("serviceKey","MobileOS","MobileApp","_type").contains(k)) uri.queryParam(k,"{p_"+k+"}"); });
        var values=new HashMap<String,Object>(); params.forEach((k,v)->values.put("p_"+k,v));
        uri.queryParam("serviceKey","{serverKey}").queryParam("MobileOS","ETC").queryParam("MobileApp","EnjoyTrip").queryParam("_type","json");
        values.put("serverKey",tourKey);
        try { return client.get().uri(uri.encode().buildAndExpand(values).toUri()).retrieve().body(String.class); }
        catch(RestClientException e) { throw new ApiException(502,"관광정보 서버에 연결하지 못했습니다."); }
    }
    record Point(@Size(max=200) String name,@NotNull @DecimalMin("-180") @DecimalMax("180") Double x,
                 @NotNull @DecimalMin("-90") @DecimalMax("90") Double y) {}
    record Directions(@NotNull @Valid Point origin,@NotNull @Valid Point destination,
                      @NotNull @Size(max=30) List<@NotNull @Valid Point> waypoints) {}
    @PostMapping("/directions") String directions(@Valid @RequestBody Directions form) {
        key(kakaoKey);
        try {
            return client.post().uri("https://apis-navi.kakaomobility.com/v1/waypoints/directions")
                .header("Authorization","KakaoAK "+kakaoKey).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("origin",form.origin(),"destination",form.destination(),"waypoints",form.waypoints(),"priority","RECOMMEND","alternatives",false,"road_details",false,"summary",false))
                .retrieve().body(String.class);
        } catch(RestClientException e) { throw new ApiException(502,"길찾기 서버에 연결하지 못했습니다."); }
    }
    @GetMapping("/regions") String regions(@RequestParam double x,@RequestParam double y) {
        key(kakaoKey);
        if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>180||Math.abs(y)>90) throw new ApiException(422,"좌표가 올바르지 않습니다.");
        try {
            return client.get().uri("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x={x}&y={y}",x,y)
                .header("Authorization","KakaoAK "+kakaoKey).retrieve().body(String.class);
        } catch(RestClientException e) { throw new ApiException(502,"지역정보 서버에 연결하지 못했습니다."); }
    }
}
