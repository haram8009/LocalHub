package com.enjoytrip;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "app.seed-demo=false"})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MemberRepository members;
    @Autowired PostRepository posts;
    static final String PASSWORD="Trip1234!";
    @BeforeEach void clear() { posts.deleteAll(); members.deleteAll(); }
    MockHttpSession account(String id) throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(java.util.Map.of("userId",id,"password",PASSWORD,"name","여행자","email",id+"@example.com"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("passwordHash").doesNotExist());
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(java.util.Map.of("userId",id,"password",PASSWORD))))
            .andExpect(status().isOk()).andReturn().getRequest().getSession();
    }
    String payload(String title) {
        return """
            {"category":"COURSE","title":"%s","content":"여행 후기 본문","authorId":"forged",
             "locations":[{"contentId":"123","name":"해운대","latitude":35.1587,"longitude":129.1583,"visitOrder":9}]}
            """.formatted(title);
    }
    long create(MockHttpSession session) throws Exception {
        var result=mvc.perform(post("/api/posts").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload("부산 코스")))
            .andExpect(status().isCreated()).andExpect(jsonPath("authorId").value("tester"))
            .andExpect(jsonPath("locations[0].visitOrder").value(1)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    @Test void authenticationAndPasswordHash() throws Exception {
        var session=account("tester");
        assertThat(members.findById("tester").orElseThrow().passwordHash).startsWith("{argon2id-v1}$argon2id$").doesNotContain(PASSWORD);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("id").value("tester"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"tester\",\"password\":\"bad\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void csrfIsRequiredAndTokenEndpointWorks() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
        var response=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var token=mapper.readTree(response.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/logout").session((MockHttpSession)response.getRequest().getSession())
            .header(token.get("headerName").asText(),token.get("token").asText())).andExpect(status().isOk());
    }
    @Test void duplicateAndInvalidSignup() throws Exception {
        account("tester");
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"tester\",\"password\":\"Trip1234!\",\"name\":\"테스터\",\"email\":\"other@example.com\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("field").value("userId"));
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"newuser\",\"password\":\"weak\",\"name\":\"테스터\",\"email\":\"new@example.com\"}"))
            .andExpect(status().isUnprocessableEntity());
    }
    @Test void postCrudSearchAndOwnership() throws Exception {
        var owner=account("tester"); long id=create(owner);
        mvc.perform(get("/api/posts/"+id)).andExpect(status().isOk()).andExpect(jsonPath("viewCount").value(1));
        mvc.perform(get("/api/posts").param("query","부산").param("placeId","123").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(1)).andExpect(jsonPath("items[0].locations[0].name").value("해운대"));
        mvc.perform(get("/api/posts").param("placeName","해운대").param("latitude","35.1587").param("longitude","129.1583"))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(1));
        var other=account("otheruser");
        mvc.perform(delete("/api/posts/"+id).session(other).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/posts/"+id).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload("수정한 코스")))
            .andExpect(status().isOk()).andExpect(jsonPath("title").value("수정한 코스"));
        mvc.perform(delete("/api/posts/"+id).session(owner).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/posts/"+id)).andExpect(status().isNotFound());
    }
    @Test void rejectsAnonymousWritesAndBadPagination() throws Exception {
        mvc.perform(post("/api/posts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload("제목"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/posts").param("page","0")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/posts").param("size","101")).andExpect(status().isUnprocessableEntity());
    }
    @Test void passwordChangeInvalidatesOtherSessionsAndWithdrawalDeletesPosts() throws Exception {
        var session=account("tester"); create(session);
        var other=(MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"tester\",\"password\":\"Trip1234!\"}")).andReturn().getRequest().getSession();
        mvc.perform(put("/api/auth/profile").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"변경이름\",\"email\":\"changed@example.com\"}")).andExpect(status().isOk()).andExpect(jsonPath("name").value("변경이름"));
        mvc.perform(put("/api/auth/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"Trip1234!\",\"newPassword\":\"Newtrip123!\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(other)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/auth/me").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"Newtrip123!\"}")).andExpect(status().isOk());
        assertThat(posts.count()).isZero(); assertThat(members.count()).isZero();
    }
    @Test void resetCodeIsRequiredAndSingleUse() throws Exception {
        var session=account("tester");
        var response=mvc.perform(post("/api/auth/reset-target").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"tester\",\"email\":\"tester@example.com\"}")).andExpect(status().isOk()).andReturn();
        var code=mapper.readTree(response.getResponse().getContentAsString()).get("devResetCode").asText();
        mvc.perform(post("/api/auth/reset-password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"wrong\",\"password\":\"Reset1234!\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/auth/reset-password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(java.util.Map.of("code",code,"password","Reset1234!")))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(java.util.Map.of("code",code,"password","Reset1234!")))).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"tester\",\"password\":\"Reset1234!\"}")).andExpect(status().isOk());
    }
    @Test void missingExternalKeyReturnsClearError() throws Exception {
        mvc.perform(get("/api/tour/areaBasedList2")).andExpect(status().isServiceUnavailable());
    }

    @Test void existingBcryptMemberUpgradesOnlyAfterSuccessfulLogin() throws Exception {
        var member = new Member("legacy", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(PASSWORD), "기존회원", "legacy@example.com");
        members.saveAndFlush(member);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"legacy\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        assertThat(members.findById("legacy").orElseThrow().passwordHash).startsWith("$2");
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"legacy\",\"password\":\"Trip1234!\"}")).andExpect(status().isOk())
            .andExpect(jsonPath("passwordHash").doesNotExist());
        assertThat(members.findById("legacy").orElseThrow().passwordHash).startsWith("{argon2id-v1}");
    }
}
