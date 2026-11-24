package com.enjoytrip;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"app.seed-demo=false", "app.tour-source=api", "app.tour-key=", "app.kakao-key="})
@AutoConfigureMockMvc
class ApiIntegrationTest extends MySqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MemberRepository members;
    @Autowired PostRepository posts;
    @Autowired JwtProvider jwt;
    static final String PASSWORD="Trip1234!";
    @BeforeEach void clear() { posts.deleteAll(); members.deleteAll(); }

    /** Access token ("Bearer ...") and the refresh cookie issued by a login. */
    record Login(String bearer, Cookie refresh) {}

    Login loginOf(MvcResult result) throws Exception {
        var node=mapper.readTree(result.getResponse().getContentAsString());
        return new Login("Bearer "+node.get("accessToken").asText(), result.getResponse().getCookie("refreshToken"));
    }
    Login login(String id,String password) throws Exception {
        var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(java.util.Map.of("userId",id,"password",password))))
            .andExpect(status().isOk()).andExpect(jsonPath("tokenType").value("Bearer"))
            .andExpect(jsonPath("passwordHash").doesNotExist()).andExpect(jsonPath("user.id").value(id)).andReturn();
        return loginOf(result);
    }
    Login account(String id) throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(java.util.Map.of("userId",id,"password",PASSWORD,"name","여행자","email",id+"@example.com"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("passwordHash").doesNotExist());
        return login(id,PASSWORD);
    }
    String payload(String title) {
        return """
            {"category":"COURSE","title":"%s","content":"여행 후기 본문","authorId":"forged",
             "locations":[{"contentId":"123","name":"해운대","latitude":35.1587,"longitude":129.1583,"visitOrder":9}]}
            """.formatted(title);
    }
    long create(Login login) throws Exception {
        var result=mvc.perform(post("/api/posts").header("Authorization",login.bearer()).contentType(MediaType.APPLICATION_JSON).content(payload("부산 코스")))
            .andExpect(status().isCreated()).andExpect(jsonPath("authorId").value("tester"))
            .andExpect(jsonPath("locations[0].visitOrder").value(1)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    @Test void authenticationAndPasswordHash() throws Exception {
        var login=account("tester");
        assertThat(members.findById("tester").orElseThrow().passwordHash).startsWith("{argon2id-v1}$argon2id$").doesNotContain(PASSWORD);
        mvc.perform(get("/api/auth/me").header("Authorization",login.bearer())).andExpect(status().isOk()).andExpect(jsonPath("id").value("tester"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"tester\",\"password\":\"bad\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void refreshCookieIsHttpOnlyAndCsrfIsNotRequired() throws Exception {
        var login=account("tester");
        assertThat(login.refresh()).isNotNull();
        assertThat(login.refresh().isHttpOnly()).isTrue();
        assertThat(login.refresh().getPath()).isEqualTo("/api/auth");
        // No CSRF token is needed for state-changing calls: the bearer token is the credential.
        mvc.perform(post("/api/auth/logout")).andExpect(status().isOk());
    }
    @Test void invalidExpiredAndTamperedTokensAreRejected() throws Exception {
        account("tester");
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        var token=jwt.createAccessToken(members.findById("tester").orElseThrow());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token+"x")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/posts").header("Authorization","Bearer "+token+"x").contentType(MediaType.APPLICATION_JSON).content(payload("제목")))
            .andExpect(status().isUnauthorized());
        var expired=new JwtProvider("0123456789abcdef0123456789abcdef",-10,1000,false).createAccessToken(members.findById("tester").orElseThrow());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
        var foreign=new JwtProvider("0123456789abcdef0123456789abcdef",1000,1000,false).createAccessToken(members.findById("tester").orElseThrow());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+foreign)).andExpect(status().isUnauthorized());
    }
    @Test void refreshRotatesTokenAndRejectsReuse() throws Exception {
        var login=account("tester");
        var refreshed=mvc.perform(post("/api/auth/refresh").cookie(login.refresh())).andExpect(status().isOk()).andReturn();
        var next=loginOf(refreshed);
        assertThat(next.refresh().getValue()).isNotEqualTo(login.refresh().getValue());
        mvc.perform(get("/api/auth/me").header("Authorization",next.bearer())).andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").cookie(login.refresh())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(next.refresh())).andExpect(status().isOk());
    }
    @Test void logoutRevokesRefreshToken() throws Exception {
        var login=account("tester");
        mvc.perform(post("/api/auth/logout").cookie(login.refresh())).andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").cookie(login.refresh())).andExpect(status().isUnauthorized());
    }
    @Test void duplicateAndInvalidSignup() throws Exception {
        account("tester");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"tester\",\"password\":\"Trip1234!\",\"name\":\"테스터\",\"email\":\"other@example.com\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("field").value("userId"));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
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
        mvc.perform(delete("/api/posts/"+id).header("Authorization",other.bearer())).andExpect(status().isForbidden());
        mvc.perform(put("/api/posts/"+id).header("Authorization",owner.bearer()).contentType(MediaType.APPLICATION_JSON).content(payload("수정한 코스")))
            .andExpect(status().isOk()).andExpect(jsonPath("title").value("수정한 코스"));
        mvc.perform(delete("/api/posts/"+id).header("Authorization",owner.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/posts/"+id)).andExpect(status().isNotFound());
    }
    @Test void rejectsAnonymousWritesAndBadPagination() throws Exception {
        mvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content(payload("제목"))).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/posts/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/posts").param("page","0")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/posts").param("size","101")).andExpect(status().isUnprocessableEntity());
    }
    @Test void passwordChangeRevokesOtherTokensAndWithdrawalDeletesPosts() throws Exception {
        var current=account("tester"); create(current);
        var other=login("tester",PASSWORD);
        mvc.perform(put("/api/auth/profile").header("Authorization",current.bearer()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"변경이름\",\"email\":\"changed@example.com\"}")).andExpect(status().isOk()).andExpect(jsonPath("name").value("변경이름"));
        var changed=mvc.perform(put("/api/auth/password").header("Authorization",current.bearer()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"Trip1234!\",\"newPassword\":\"Newtrip123!\"}")).andExpect(status().isOk()).andReturn();
        var renewed=loginOf(changed);
        mvc.perform(get("/api/auth/me").header("Authorization",other.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization",current.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(other.refresh())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization",renewed.bearer())).andExpect(status().isOk());
        mvc.perform(delete("/api/auth/me").header("Authorization",renewed.bearer()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"Newtrip123!\"}")).andExpect(status().isOk());
        assertThat(posts.count()).isZero(); assertThat(members.count()).isZero();
        mvc.perform(post("/api/auth/refresh").cookie(renewed.refresh())).andExpect(status().isUnauthorized());
    }
    @Test void resetCodeIsRequiredAndSingleUse() throws Exception {
        account("tester");
        var response=mvc.perform(post("/api/auth/reset-target").contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"tester\",\"email\":\"tester@example.com\"}")).andExpect(status().isOk()).andReturn();
        var code=mapper.readTree(response.getResponse().getContentAsString()).get("devResetCode").asText();
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"tester\",\"code\":\"wrong\",\"password\":\"Reset1234!\"}")).andExpect(status().isUnprocessableEntity());
        var body=mapper.writeValueAsString(java.util.Map.of("userId","tester","code",code,"password","Reset1234!"));
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableEntity());
        login("tester","Reset1234!");
    }
    @Test void resetCodeLocksAfterFiveWrongAttempts() throws Exception {
        account("tester");
        var response=mvc.perform(post("/api/auth/reset-target").contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"tester\",\"email\":\"tester@example.com\"}")).andExpect(status().isOk()).andReturn();
        var code=mapper.readTree(response.getResponse().getContentAsString()).get("devResetCode").asText();
        for(int i=0;i<5;i++)
            mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"tester\",\"code\":\"bad\",\"password\":\"Reset1234!\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(java.util.Map.of("userId","tester","code",code,"password","Reset1234!")))).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"tester\",\"password\":\"Reset1234!\"}")).andExpect(status().isUnauthorized());
    }
    @Test void missingExternalKeyReturnsClearError() throws Exception {
        mvc.perform(get("/api/tour/areaBasedList2")).andExpect(status().isServiceUnavailable());
    }

    @Test void existingBcryptMemberUpgradesOnlyAfterSuccessfulLogin() throws Exception {
        var member = new Member("legacy", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(PASSWORD), "기존회원", "legacy@example.com");
        members.saveAndFlush(member);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"legacy\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        assertThat(members.findById("legacy").orElseThrow().passwordHash).startsWith("$2");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"legacy\",\"password\":\"Trip1234!\"}")).andExpect(status().isOk())
            .andExpect(jsonPath("passwordHash").doesNotExist());
        assertThat(members.findById("legacy").orElseThrow().passwordHash).startsWith("{argon2id-v1}");
    }
}
