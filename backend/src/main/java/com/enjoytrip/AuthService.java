package com.enjoytrip;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Locale;

@Service
public class AuthService {
    final MemberRepository members;
    final PasswordEncoder encoder;
    AuthService(MemberRepository members, PasswordEncoder encoder) { this.members = members; this.encoder = encoder; }
    static String email(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    static void password(String value) {
        if (value == null || !value.matches("(?s)(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])[^\\s]{8,20}"))
            throw new ApiException(422, "비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자로 입력해 주세요.", "password");
    }
    public Member require(HttpSession session) {
        return require(session, false);
    }
    public Member requireForUpdate(HttpSession session) {
        return require(session, true);
    }
    private Member require(HttpSession session, boolean lock) {
        var id = session.getAttribute("memberId");
        var member = id instanceof String s
            ? (lock ? members.findLockedById(s) : members.findById(s)).orElse(null) : null;
        if (member == null || !Long.valueOf(member.credentialVersion).equals(session.getAttribute("credentialVersion")))
            throw new ApiException(401, "로그인이 필요합니다.");
        return member;
    }
    void checkPassword(Member member, String password) {
        if (password == null || !encoder.matches(password, member.passwordHash))
            throw new ApiException(403, "비밀번호가 일치하지 않습니다.");
    }
    static User user(Member m) { return new User(m.id, m.name, m.email); }
    record User(String id, String name, String email) {}
}
