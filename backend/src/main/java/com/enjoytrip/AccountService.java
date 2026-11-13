package com.enjoytrip;

import jakarta.servlet.http.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.Map;
import static com.enjoytrip.AuthRequests.*;

@Service
@Transactional
public class AccountService {
    final AuthService auth;
    final PostRepository posts;
    final Environment environment;
    final PostSearchIndexer indexer;
    AccountService(AuthService auth, PostRepository posts, Environment environment, PostSearchIndexer indexer) {
        this.auth = auth; this.posts = posts; this.environment = environment; this.indexer = indexer;
    }
    @Transactional(readOnly=true)
    public AuthService.User me(HttpSession session) { return AuthService.user(auth.require(session)); }

    public AuthService.User signup(Signup form) {
        AuthService.password(form.password());
        if (auth.members.existsById(form.userId())) throw new ApiException(409, "이미 사용 중인 아이디입니다.", "userId");
        String email = AuthService.email(form.email());
        if (auth.members.existsByEmail(email)) throw new ApiException(409, "이미 가입된 이메일입니다.", "email");
        return AuthService.user(auth.members.insert(new Member(form.userId(), auth.encoder.encode(form.password()), form.name().trim(), email)));
    }
    public AuthService.User login(Login form, HttpServletRequest request) {
        var member = auth.members.findLockedById(form.userId().trim()).orElse(null);
        if (member == null || !auth.encoder.matches(form.password(), member.passwordHash))
            throw new ApiException(401, "아이디 또는 비밀번호가 일치하지 않습니다.");
        if (auth.encoder.upgradeEncoding(member.passwordHash))
            member.passwordHash = auth.encoder.encode(form.password());
        auth.members.update(member);
        var session = request.getSession();
        var authenticated = member;
        AfterCommit.run(() -> {
            request.changeSessionId();
            session.setAttribute("memberId", authenticated.id);
            session.setAttribute("credentialVersion", authenticated.credentialVersion);
        });
        return AuthService.user(member);
    }
    public void logout(HttpSession session) { AfterCommit.run(session::invalidate); }
    public AuthService.User profile(Profile form, HttpSession session) {
        var member = auth.requireForUpdate(session);
        String email = AuthService.email(form.email());
        if (!email.equals(member.email) && auth.members.existsByEmail(email)) throw new ApiException(409, "이미 가입된 이메일입니다.", "email");
        member.name = form.name().trim(); member.email = email;
        auth.members.update(member);
        return AuthService.user(member);
    }
    public void password(Passwords form, HttpSession session) {
        var member = auth.requireForUpdate(session);
        auth.checkPassword(member, form.currentPassword());
        AuthService.password(form.newPassword());
        if (form.currentPassword().equals(form.newPassword())) throw new ApiException(422, "현재 비밀번호와 다른 비밀번호를 입력해 주세요.");
        member.passwordHash = auth.encoder.encode(form.newPassword()); member.credentialVersion++;
        auth.members.update(member);
        AfterCommit.run(() -> session.setAttribute("credentialVersion", member.credentialVersion));
    }
    public void withdraw(Password form, HttpSession session) {
        var member = auth.requireForUpdate(session); auth.checkPassword(member, form.password());
        indexer.removeByAuthor(member.id);
        posts.deleteByAuthorId(member.id);
        auth.members.delete(member); AfterCommit.run(session::invalidate);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> findId(FindId form) {
        var member = auth.members.findByNameAndEmail(form.name().trim(), AuthService.email(form.email()))
            .orElseThrow(() -> new ApiException(404, "일치하는 회원 정보를 찾을 수 없습니다."));
        int visible = Math.min(3, Math.max(1, member.id.length()-2));
        return Map.of("maskedUserId", member.id.substring(0,visible)+"*".repeat(member.id.length()-visible), "createdAt", member.createdAt);
    }
    public Map<String,String> resetTarget(Target form, HttpSession session) {
        // Local classroom workflow only. Never expose a recovery code outside local profile.
        if (!environment.acceptsProfiles(Profiles.of("local")))
            throw new ApiException(503, "비밀번호 재설정은 현재 로컬 개발 모드에서만 지원됩니다.");
        var previous = (Instant) session.getAttribute("resetIssuedAt");
        if (previous != null && previous.plusSeconds(60).isAfter(Instant.now())) throw new ApiException(429, "1분 후 다시 요청해 주세요.");
        var member = auth.members.findByIdAndEmail(form.userId().trim(), AuthService.email(form.email()))
            .orElseThrow(() -> new ApiException(404, "일치하는 회원 정보를 찾을 수 없습니다."));
        String code = "%06d".formatted(new SecureRandom().nextInt(1000000));
        session.setAttribute("resetUser", member.id); session.setAttribute("resetCode", code);
        session.setAttribute("resetIssuedAt", Instant.now()); session.setAttribute("resetAttempts", 0);
        return Map.of("userId", member.id, "email", member.email, "devResetCode", code);
    }
    public void reset(Reset form, HttpSession session) {
        synchronized(session) {
            var issued = (Instant) session.getAttribute("resetIssuedAt");
            var attempts = (Integer) session.getAttribute("resetAttempts");
            if (issued == null || issued.plusSeconds(600).isBefore(Instant.now()) || attempts == null || attempts >= 5 || session.getAttribute("resetCode") == null)
                throw new ApiException(422, "재설정 요청이 만료되었습니다. 비밀번호 찾기를 다시 진행해 주세요.");
            session.setAttribute("resetAttempts", attempts+1);
            if (!form.code().equals(session.getAttribute("resetCode"))) throw new ApiException(422, "인증 코드가 일치하지 않습니다.");
            AuthService.password(form.password());
            var member = auth.members.findLockedById((String)session.getAttribute("resetUser")).orElseThrow(() -> new ApiException(404, "회원을 찾을 수 없습니다."));
            member.passwordHash = auth.encoder.encode(form.password()); member.credentialVersion++;
            auth.members.update(member);
            AfterCommit.run(session::invalidate);
        }
    }
}
