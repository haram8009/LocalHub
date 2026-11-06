package com.enjoytrip;

import java.time.Instant;
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

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:nearby-post-test;DB_CLOSE_DELAY=-1",
    "app.seed-demo=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
class NearbyPostIntegrationTest {
    @Autowired PostRepository posts;
    @Autowired MockMvc mvc;
    @Autowired EntityManagerFactory emf;

    @BeforeEach void clear() { posts.deleteAll(); }

    PostLocation location(String id, Double latitude, Double longitude) {
        var location = new PostLocation(); location.contentId = id; location.name = "장소 " + id;
        location.address = "서울특별시"; location.category = "음식";
        location.latitude = latitude; location.longitude = longitude;
        return location;
    }

    TravelPost post(String title, PostLocation... locations) {
        var post = new TravelPost(); post.category = "REVIEW"; post.title = title; post.content = "게시글 내용";
        post.authorId = "tester"; post.authorName = "여행자";
        post.createdAt = Instant.parse("2026-10-01T00:00:00Z"); post.updatedAt = post.createdAt;
        for (int i = 0; i < locations.length; i++) {
            locations[i].visitOrder = i + 1; post.locations.add(locations[i]);
        }
        return posts.saveAndFlush(post);
    }

    @Test void ranksPostsByTheirClosestPlaceAndDoesNotDuplicatePosts() throws Exception {
        var multiple = post("두 장소", location("farther", 37.53, 127.0), location("nearest", 37.501, 127.0));
        var second = post("중간 장소", location("second", 37.51, 127.0));
        var third = post("세 번째", location("third", 37.52, 127.0));
        post("네 번째", location("fourth", 37.54, 127.0));

        mvc.perform(get("/api/posts/nearby").param("latitude", "37.5").param("longitude", "127"))
            .andExpect(status().isOk()).andExpect(jsonPath("items.length()").value(3))
            .andExpect(jsonPath("items[0].id").value(multiple.id))
            .andExpect(jsonPath("items[0].locationCount").value(2))
            .andExpect(jsonPath("items[0].distanceKm").value(org.hamcrest.Matchers.closeTo(0.1112, 0.001), Double.class))
            .andExpect(jsonPath("items[0].nearestLocation.contentId").value("nearest"))
            .andExpect(jsonPath("items[0].nearestLocation.name").value("장소 nearest"))
            .andExpect(jsonPath("items[0].category").value("REVIEW"))
            .andExpect(jsonPath("items[1].id").value(second.id)).andExpect(jsonPath("items[2].id").value(third.id));
        assertThat(posts.findById(multiple.id).orElseThrow().viewCount).isZero();
    }

    @Test void keepsOnlyPlacesInsideFiveKilometersAndAllowsFewerThanThree() throws Exception {
        var nearby = post("가까움", location("inside", 37.54, 127.0));
        post("5km 밖", location("outside", 37.55, 127.0));
        post("경계 상자의 모서리", location("corner", 37.54, 127.04));
        post("유효하지 않은 좌표", location("null", null, null), location("zero", 0.0, 0.0), location("overseas", 1.0, 127.0));
        post("장소 없음");

        mvc.perform(get("/api/posts/nearby").param("latitude", "37.5").param("longitude", "127"))
            .andExpect(status().isOk()).andExpect(jsonPath("items.length()").value(1))
            .andExpect(jsonPath("items[0].id").value(nearby.id));
        mvc.perform(get("/api/posts/nearby").param("latitude", "35.16").param("longitude", "129.16"))
            .andExpect(status().isOk()).andExpect(jsonPath("items").isEmpty());
    }

    @Test void resolvesEqualDistanceByNewestCreationThenIdAndAppliesLimit() throws Exception {
        var old = post("오래된 글", location("same", 37.5, 127.0));
        var newest = post("최근 글", location("same", 37.5, 127.0));
        var higherId = post("높은 ID", location("same", 37.5, 127.0));
        newest.createdAt = Instant.parse("2026-10-02T00:00:00Z"); posts.saveAndFlush(newest);
        mvc.perform(get("/api/posts/nearby").param("latitude", "37.5").param("longitude", "127").param("limit", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("items.length()").value(2))
            .andExpect(jsonPath("items[0].id").value(newest.id)).andExpect(jsonPath("items[1].id").value(higherId.id));
        assertThat(old.id).isLessThan(higherId.id);
    }

    @Test void rejectsMissingNonFiniteOverseasCoordinatesAndInvalidLimits() throws Exception {
        mvc.perform(get("/api/posts/nearby")).andExpect(status().isBadRequest());
        for (String latitude : new String[]{"NaN", "Infinity", "wrong", "0", "41"})
            mvc.perform(get("/api/posts/nearby").param("latitude", latitude).param("longitude", "127"))
                .andExpect(status().isBadRequest());
        for (String longitude : new String[]{"NaN", "Infinity", "0", "134"})
            mvc.perform(get("/api/posts/nearby").param("latitude", "37.5").param("longitude", longitude))
                .andExpect(status().isBadRequest());
        for (String limit : new String[]{"0", "11", "wrong"})
            mvc.perform(get("/api/posts/nearby").param("latitude", "37.5").param("longitude", "127").param("limit", limit))
                .andExpect(status().isBadRequest());
    }

    @Test void readsProjectionsWithoutLoadingPostEntitiesOrTheirLocationCollections() throws Exception {
        for (int i = 0; i < 20; i++) post("가까운 글 " + i, location("close", 37.5, 127.0));
        for (int i = 0; i < 20; i++) post("먼 글 " + i, location("far", 35.16, 129.16));
        var statistics = emf.unwrap(SessionFactory.class).getStatistics(); statistics.clear();
        mvc.perform(get("/api/posts/nearby").param("latitude", "37.5").param("longitude", "127"))
            .andExpect(status().isOk()).andExpect(jsonPath("items.length()").value(3));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(statistics.getCollectionFetchCount()).isZero();
    }
}
