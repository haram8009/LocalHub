package com.enjoytrip;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:attractions;DB_CLOSE_DELAY=-1", "app.seed-demo=false", "app.tour-source=database"})
@AutoConfigureMockMvc
class AttractionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void setup() {
        jdbc.execute("create table if not exists sidos(sido_code int, sido_name varchar(20))");
        jdbc.execute("create table if not exists guguns(sido_code int, gugun_code int, gugun_name varchar(20))");
        jdbc.execute("create table if not exists attractions(no int primary key,content_id int,title varchar(500),content_type_id int,area_code int,si_gun_gu_code int,first_image1 varchar(100),first_image2 varchar(100),map_level int,latitude decimal(20,17),longitude decimal(20,17),tel varchar(20),addr1 varchar(100),addr2 varchar(100),homepage varchar(1000),overview varchar(10000))");
        jdbc.update("delete from attractions"); jdbc.update("delete from guguns"); jdbc.update("delete from sidos");
        jdbc.update("insert into sidos values(1,'서울'),(6,'부산')");
        jdbc.update("insert into guguns values(1,1,'강남구'),(6,1,'강서구')");
        jdbc.update("insert into attractions(no,content_id,title,content_type_id,area_code,si_gun_gu_code,latitude,longitude,overview,first_image1) values(1,101,'서울 관광지',12,1,1,37.5,127.0,'상세 설명','https://example.com/photo.jpg'),(2,102,'부산 음식점',39,6,1,35.1,129.0,'식당 설명',null),(3,103,'서울 100% 식당',39,1,1,37.51,127.0,'',null)");
    }
    @Test void regionAndTypeFilteringWithPagination() throws Exception {
        mvc.perform(get("/api/tour/ldongCode2").param("lDongRegnCd","6"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.response.body.items.item[0].name").value("강서구"));
        mvc.perform(get("/api/tour/areaBasedList2").param("lDongRegnCd","1").param("lDongSignguCd","1").param("contentTypeId","39"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.response.body.totalCount").value(1))
            .andExpect(jsonPath("$.response.body.items.item[0].contentid").value(103));
        mvc.perform(get("/api/tour/areaBasedList2").param("numOfRows","1").param("pageNo","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.response.body.totalCount").value(3))
            .andExpect(jsonPath("$.response.body.items.item.length()").value(1));
    }
    @Test void nearbyAndLiteralKeyword() throws Exception {
        mvc.perform(get("/api/tour/locationBasedList2").param("mapX","127").param("mapY","37.5").param("radius","2000").param("arrange","E"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.response.body.totalCount").value(2))
            .andExpect(jsonPath("$.response.body.items.item[0].contentid").value(101));
        mvc.perform(get("/api/tour/searchKeyword2").param("keyword","%"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.response.body.totalCount").value(1));
    }
    @Test void detailAndInvalidRequests() throws Exception {
        mvc.perform(get("/api/tour/detailCommon2").param("contentId","101"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.response.body.items.item[0].overview").value("상세 설명"));
        mvc.perform(get("/api/tour/detailImage2").param("contentId","101"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.response.body.items.item[0].originimgurl").value("https://example.com/photo.jpg"));
        mvc.perform(get("/api/tour/detailCommon2").param("contentId","999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/tour/areaBasedList2").param("pageNo","0")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/tour/areaBasedList2").param("lDongSignguCd","1")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/tour/locationBasedList2").param("mapX","NaN").param("mapY","37").param("radius","1000")).andExpect(status().isUnprocessableEntity());
    }
}
