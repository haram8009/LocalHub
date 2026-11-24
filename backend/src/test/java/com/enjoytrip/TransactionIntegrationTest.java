package com.enjoytrip;

import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"app.seed-demo=false"})
class TransactionIntegrationTest extends MySqlIntegrationTest {
    @Autowired PostService service;
    @Autowired AccountService accounts;
    @Autowired PostRepository posts;
    @Autowired MemberRepository members;
    @Autowired PostSearchIndexRepository index;
    @MockitoSpyBean SearchIndexMapper indexMapper;
    @MockitoSpyBean MemberMapper memberMapper;
    AuthPrincipal session;
    static final String PASSWORD="Test1234!";

    @BeforeEach void setup() {
        posts.deleteAll(); members.deleteAll();
        accounts.signup(new AuthRequests.Signup("tester",PASSWORD,"여행자","tester@example.com"));
        session=new AuthPrincipal("tester",0L);
    }
    PostInput input(String title) {
        var location=new PostLocation(); location.name="서울"; location.contentId="101";
        return new PostInput("COURSE",title,"여행 내용",List.of(location));
    }
    @Test void indexFailureRollsBackPostAndLocations() {
        doThrow(new IllegalStateException("index failure")).when(indexMapper).add(any());
        assertThatThrownBy(()->service.create(input("서울여행"),session)).isInstanceOf(IllegalStateException.class);
        assertThat(posts.count()).isZero();
        assertThat(index.findContaining(List.of("서울"))).isEmpty();
    }
    @Test void failedEditRestoresPostLocationsAndOldIndex() {
        var post=service.create(input("부산여행"),session);
        doThrow(new IllegalStateException("index failure")).when(indexMapper).add(any());
        assertThatThrownBy(()->service.update(post.id,input("서울여행"),session)).isInstanceOf(IllegalStateException.class);
        var restored=posts.findById(post.id).orElseThrow();
        assertThat(restored.title).isEqualTo("부산여행");
        assertThat(restored.locations).hasSize(1);
        assertThat(index.findContaining(List.of("부산"))).hasSize(1);
        assertThat(index.findContaining(List.of("서울"))).isEmpty();
    }
    @Test void failedWithdrawalRestoresMemberPostsAndIndex() {
        service.create(input("부산여행"),session);
        doThrow(new IllegalStateException("member delete failure")).when(memberMapper).delete("tester");
        assertThatThrownBy(()->accounts.withdraw(new AuthRequests.Password(PASSWORD),session)).isInstanceOf(IllegalStateException.class);
        assertThat(members.existsById("tester")).isTrue();
        assertThat(posts.count()).isEqualTo(1);
        assertThat(index.findContaining(List.of("부산"))).hasSize(1);
        assertThat(accounts.me(session).id()).isEqualTo("tester");
    }
}
