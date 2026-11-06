package com.enjoytrip;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:recommend-test;DB_CLOSE_DELAY=-1", "app.seed-demo=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
class CourseRecommendationIntegrationTest {
    @Autowired PostRepository posts;
    @Autowired MockMvc mvc;
    @Autowired EntityManagerFactory emf;
    @BeforeEach void clear() { posts.deleteAll(); }
    TravelPost post(String title, String... stops) {
        var p = new TravelPost(); p.category = "COURSE"; p.title = title; p.content = "내용";
        p.authorId = "tester"; p.authorName = "여행자";
        p.createdAt = Instant.parse("2026-10-01T00:00:00Z"); p.updatedAt = p.createdAt;
        for (int i = 0; i < stops.length; i++) {
            var l = new PostLocation(); l.contentId = stops[i]; l.name = "동일한 이름"; l.visitOrder = i + 1;
            p.locations.add(l);
        }
        return posts.saveAndFlush(p);
    }
    @Test void ranksByJaccardAndLcsAndReturnsDisplayFields() throws Exception {
        var base = post("기준", "A", "B", "C", "D");
        var partial = post("부분", "A", "B", "E", "D");
        var reverse = post("역순", "D", "C", "B", "A");
        post("불일치", "X"); post("빈 코스");
        mvc.perform(get("/api/posts/" + base.id + "/similar"))
            .andExpect(status().isOk()).andExpect(jsonPath("items.length()").value(2))
            .andExpect(jsonPath("items[0].id").value(reverse.id)).andExpect(jsonPath("items[0].score").value(0.7))
            .andExpect(jsonPath("items[0].commonPlaceCount").value(4)).andExpect(jsonPath("items[0].lcsLength").value(1))
            .andExpect(jsonPath("items[0].locationCount").value(4)).andExpect(jsonPath("items[0].authorName").value("여행자"))
            .andExpect(jsonPath("items[1].id").value(partial.id));
        assertThat(posts.findById(base.id).orElseThrow().viewCount).isZero();
    }
    @Test void usesStableTiesAndReflectsEditsAndDeletion() throws Exception {
        var base = post("기준", "A", "B");
        var older = post("동점1", "A", "B");
        var newer = post("동점2", "A", "B");
        mvc.perform(get("/api/posts/" + base.id + "/similar").param("limit", "1"))
            .andExpect(jsonPath("items[0].id").value(newer.id));
        newer.locations.clear(); posts.saveAndFlush(newer);
        mvc.perform(get("/api/posts/" + base.id + "/similar"))
            .andExpect(jsonPath("items.length()").value(1)).andExpect(jsonPath("items[0].id").value(older.id));
        posts.deleteById(older.id);
        mvc.perform(get("/api/posts/" + base.id + "/similar")).andExpect(jsonPath("items").isEmpty());
    }
    @Test void validatesLimitsAndDoesNotMergeDemoOrNames() throws Exception {
        var demo = post("샘플", "demo-1-1");
        post("실제 장소", "12345");
        mvc.perform(get("/api/posts/" + demo.id + "/similar")).andExpect(jsonPath("items").isEmpty());
        mvc.perform(get("/api/posts/" + demo.id + "/similar").param("limit", "11")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/posts/999999/similar")).andExpect(status().isNotFound());
    }
    @Test void batchQueriesAvoidPerCandidateLocationLoads() throws Exception {
        var base = post("기준", "A", "B");
        for (int i = 0; i < 20; i++) post("후보" + i, "A", "B", "C" + i);
        var statistics = emf.unwrap(SessionFactory.class).getStatistics(); statistics.clear();
        mvc.perform(get("/api/posts/" + base.id + "/similar"))
            .andExpect(status().isOk()).andExpect(jsonPath("items.length()").value(3));
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
    }
}
