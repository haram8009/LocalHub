package com.enjoytrip;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.time.Instant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:search-test;DB_CLOSE_DELAY=-1", "app.seed-demo=false"})
@AutoConfigureMockMvc
class PostSearchIntegrationTest {
    @Autowired PostRepository posts;
    @Autowired PostSearchIndexer indexer;
    @Autowired PostSearchService search;
    @Autowired SearchIndexInitializer initializer;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired PostSearchIndexRepository index;
    @Autowired MockMvc mvc;
    TransactionTemplate tx;
    @BeforeEach void clear() { tx = new TransactionTemplate(manager); posts.deleteAll(); }
    TravelPost add(String title, String body, String category, String author) {
        return tx.execute(status -> {
            var p = new TravelPost(); p.category = category; p.title = title; p.content = body;
            p.authorId = author; p.authorName = "여행자"; p.createdAt = Instant.parse("2026-10-01T00:00:00Z"); p.updatedAt = p.createdAt;
            var location = new PostLocation(); location.contentId = "123"; location.name = "해운대"; location.visitOrder = 1;
            p.locations.add(location);
            posts.saveAndFlush(p); indexer.replace(p); return p;
        });
    }
    @Test void substringAndSearchRanksAndDoesNotInflateScores() throws Exception {
        var first = add("부산에서 바다를", "후기", "COURSE", "one");
        var second = add("부산 부산여행 부산에서", "바다 여행", "COURSE", "two");
        var third = add("부산 바다", "부산여행 바다를", "REVIEW", "three");
        add("서울 산책", "후기", "COURSE", "four");
        mvc.perform(get("/api/posts/search").param("query", "부산 바다").param("mode", "AND"))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(3))
            .andExpect(jsonPath("items[0].id").value(third.id)).andExpect(jsonPath("items[0].relevanceScore").value(6))
            .andExpect(jsonPath("items[1].id").value(first.id)).andExpect(jsonPath("items[1].relevanceScore").value(4))
            .andExpect(jsonPath("items[2].id").value(second.id)).andExpect(jsonPath("items[2].relevanceScore").value(3));
        mvc.perform(get("/api/posts/search").param("query", "부산 부산"))
            .andExpect(jsonPath("items[0].relevanceScore").value(3))
            .andExpect(jsonPath("items[1].relevanceScore").value(2));
        mvc.perform(get("/api/posts/search").param("query", "부산 존재하지않음").param("mode", "OR"))
            .andExpect(jsonPath("total").value(0));
        mvc.perform(get("/api/posts/search").param("query", "부산 존재하지않음"))
            .andExpect(jsonPath("total").value(0));
    }
    @Test void appliesFiltersBeforeRankingPaginationAndPreservesLocationSnapshots() throws Exception {
        add("부산여행", "바다", "REVIEW", "one");
        add("부산여행", "바다", "COURSE", "one");
        var last = add("부산여행", "바다", "COURSE", "one");
        add("부산여행", "바다", "COURSE", "other");
        mvc.perform(get("/api/posts/search").param("query", "부산").param("category", "COURSE").param("authorId", "one")
                .param("placeName", "해운대").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(2)).andExpect(jsonPath("totalPages").value(2))
            .andExpect(jsonPath("items[0].id").value(last.id)).andExpect(jsonPath("items[0].locations[0].contentId").value("123"));
        mvc.perform(get("/api/posts/search").param("query", "부산").param("category", "COURSE").param("authorId", "one")
                .param("size", "1").param("page", "3"))
            .andExpect(jsonPath("total").value(2)).andExpect(jsonPath("items").isEmpty());
        mvc.perform(get("/api/posts/search").param("query", "부산").param("placeId", "not-same"))
            .andExpect(jsonPath("total").value(0));
    }
    @Test void normalizesNfkcCasePunctuationAndSupportsOldQueryEndpoint() throws Exception {
        add("ＴＲＩＰ 부산,바다", "정규화", "COURSE", "one");
        mvc.perform(get("/api/posts/search").param("query", "trip 부산 바다")).andExpect(jsonPath("total").value(1));
        mvc.perform(get("/api/posts").param("query", "부산 바다")).andExpect(jsonPath("total").value(1));
        mvc.perform(get("/api/posts/search").param("query", "부산바다")).andExpect(jsonPath("total").value(0));
        mvc.perform(get("/api/posts/search").param("query", " %% __ "))
            .andExpect(jsonPath("total").value(1)).andExpect(jsonPath("items[0].relevanceScore").doesNotExist());
    }
    @Test void editDeleteAndRollbackKeepTheIndexConsistent() throws Exception {
        var original = add("부산여행", "바다", "COURSE", "one");
        assertThatThrownBy(() -> tx.execute(status -> {
            var p = posts.findLockedById(original.id).orElseThrow(); p.title = "서울여행"; indexer.replace(p);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);
        mvc.perform(get("/api/posts/search").param("query", "부산")).andExpect(jsonPath("total").value(1));
        mvc.perform(get("/api/posts/search").param("query", "서울")).andExpect(jsonPath("total").value(0));
        tx.executeWithoutResult(status -> { var p = posts.findLockedById(original.id).orElseThrow(); p.title = "서울여행"; indexer.replace(p); });
        mvc.perform(get("/api/posts/search").param("query", "부산")).andExpect(jsonPath("total").value(0));
        mvc.perform(get("/api/posts/search").param("query", "서울")).andExpect(jsonPath("total").value(1));
        posts.deleteById(original.id);
        mvc.perform(get("/api/posts/search").param("query", "서울")).andExpect(jsonPath("total").value(0));
    }
    @Test void validatesInputsAndCanRebuildExistingPosts() throws Exception {
        add("부산여행", "바다", "COURSE", "one");
        // Legacy mode parameters no longer change the AND-only search behavior.
        mvc.perform(get("/api/posts/search").param("query", "부산").param("mode", "BAD")).andExpect(status().isOk());
        mvc.perform(get("/api/posts/search").param("query", "부산").param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/posts/search").param("query", "부산").param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/posts/search").param("query", "a".repeat(101))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/posts/search").param("query", "a b c d e f g h i j k")).andExpect(status().isBadRequest());
        tx.executeWithoutResult(status -> em.remove(em.find(SearchIndexState.class, "posts")));
        initializer.run();
        mvc.perform(get("/api/posts/search").param("query", "부산")).andExpect(jsonPath("total").value(1));
        initializer.run();
        mvc.perform(get("/api/posts/search").param("query", "부산")).andExpect(jsonPath("total").value(1));
    }

    @Test void serializableReadDoesNotMixOldPostingsWithNewPostContent() {
        var original = add("부산여행", "바다", "COURSE", "one");
        var reader = new TransactionTemplate(manager);
        reader.setReadOnly(true);
        reader.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_SERIALIZABLE);
        reader.executeWithoutResult(status -> {
            assertThat(index.findContaining(List.of("부산"))).hasSize(1);
            var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                pool.submit(() -> tx.executeWithoutResult(writer -> {
                    var p = posts.findLockedById(original.id).orElseThrow(); p.title = "서울여행"; indexer.replace(p);
                })).get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) { throw new AssertionError(e); }
            finally { pool.shutdownNow(); }
            assertThat(posts.findWithLocationsByIds(List.of(original.id)).get(0).title).isEqualTo("부산여행");
            assertThat(index.findContaining(List.of("서울"))).isEmpty();
        });
        assertThat(search.search(Map.of("query", "서울")).get("total")).isEqualTo(1L);
    }
}
