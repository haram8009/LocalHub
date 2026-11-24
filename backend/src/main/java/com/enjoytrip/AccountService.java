package com.enjoytrip;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import static com.enjoytrip.AuthRequests.*;

@Service
@Transactional
public class AccountService {
    final AuthService auth;
    final PostRepository posts;
    final Environment environment;
    final PostSearchIndexer indexer;
    final JwtProvider jwt;
    final TokenMapper tokens;
    private final SecureRandom random = new SecureRandom();
    AccountService(AuthService auth, PostRepository posts, Environment environment, PostSearchIndexer indexer, JwtProvider jwt, TokenMapper tokens) {
        this.auth = auth; this.posts = posts; this.environment = environment; this.indexer = indexer; this.jwt = jwt; this.tokens = tokens;
    }
    /** Access token (JSON body) + refresh token (HttpOnly cookie, set by the controller). */
    public record Tokens(String accessToken, String refreshToken, AuthService.User user) {}

    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Tokens issue(Member member) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.insertRefresh(sha256(refresh), member.id, Instant.now().plusSeconds(jwt.refreshExpirySeconds));
        return new Tokens(jwt.createAccessToken(member), refresh, AuthService.user(member));
    }
    @Transactional(readOnly=true)
    public AuthService.User me(AuthPrincipal principal) { return AuthService.user(auth.require(principal)); }

    public AuthService.User signup(Signup form) {
        AuthService.password(form.password());
        if (auth.members.existsById(form.userId())) throw new ApiException(409, "이미 사용 중인 아이디입니다.", "userId");
        String email = AuthService.email(form.email());
        if (auth.members.existsByEmail(email)) throw new ApiException(409, "이미 가입된 이메일입니다.", "email");
        return AuthService.user(auth.members.insert(new Member(form.userId(), auth.encoder.encode(form.password()), form.name().trim(), email)));
    }
    public Tokens login(Login form) {
        var member = auth.members.findLockedById(form.userId().trim()).orElse(null);
        if (member == null || !auth.encoder.matches(form.password(), member.passwordHash))
            throw new ApiException(401, "아이디 또는 비밀번호가 일치하지 않습니다.");
        if (auth.encoder.upgradeEncoding(member.passwordHash))
            member.passwordHash = auth.encoder.encode(form.password());
        auth.members.update(member);
        tokens.deleteExpiredRefresh(Instant.now());
        return issue(member);
    }
    /** Rotates the refresh token: the presented one is consumed and a new pair is returned. */
    @Transactional(noRollbackFor = ApiException.class)
    public Tokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) throw new ApiException(401, "로그인이 필요합니다.");
        var hash = sha256(refreshToken);
        var row = tokens.findRefreshForUpdate(hash).orElseThrow(() -> new ApiException(401, "로그인이 필요합니다."));
        tokens.deleteRefresh(hash);
        if (row.expiresAt.isBefore(Instant.now())) throw new ApiException(401, "로그인이 만료되었습니다. 다시 로그인해 주세요.");
        var member = auth.members.findLockedById(row.memberId).orElseThrow(() -> new ApiException(401, "로그인이 필요합니다."));
        return issue(member);
    }
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) tokens.deleteRefresh(sha256(refreshToken));
    }
    public AuthService.User profile(Profile form, AuthPrincipal principal) {
        var member = auth.requireForUpdate(principal);
        String email = AuthService.email(form.email());
        if (!email.equals(member.email) && auth.members.existsByEmail(email)) throw new ApiException(409, "이미 가입된 이메일입니다.", "email");
        member.name = form.name().trim(); member.email = email;
        auth.members.update(member);
        return AuthService.user(member);
    }
    /** Changing the password bumps credentialVersion, revoking all other tokens; this device gets a fresh pair. */
    public Tokens password(Passwords form, AuthPrincipal principal) {
        var member = auth.requireForUpdate(principal);
        auth.checkPassword(member, form.currentPassword());
        AuthService.password(form.newPassword());
        if (form.currentPassword().equals(form.newPassword())) throw new ApiException(422, "현재 비밀번호와 다른 비밀번호를 입력해 주세요.");
        member.passwordHash = auth.encoder.encode(form.newPassword()); member.credentialVersion++;
        auth.members.update(member);
        tokens.deleteRefreshByMember(member.id);
        return issue(member);
    }
    public void withdraw(Password form, AuthPrincipal principal) {
        var member = auth.requireForUpdate(principal); auth.checkPassword(member, form.password());
        indexer.removeByAuthor(member.id);
        posts.deleteByAuthorId(member.id);
        auth.members.delete(member);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> findId(FindId form) {
        var member = auth.members.findByNameAndEmail(form.name().trim(), AuthService.email(form.email()))
            .orElseThrow(() -> new ApiException(404, "일치하는 회원 정보를 찾을 수 없습니다."));
        int visible = Math.min(3, Math.max(1, member.id.length()-2));
        return Map.of("maskedUserId", member.id.substring(0,visible)+"*".repeat(member.id.length()-visible), "createdAt", member.createdAt);
    }
    public Map<String,String> resetTarget(Target form) {
        // Local classroom workflow only. Never expose a recovery code outside local profile.
        if (!environment.acceptsProfiles(Profiles.of("local")))
            throw new ApiException(503, "비밀번호 재설정은 현재 로컬 개발 모드에서만 지원됩니다.");
        var member = auth.members.findByIdAndEmail(form.userId().trim(), AuthService.email(form.email()))
            .orElseThrow(() -> new ApiException(404, "일치하는 회원 정보를 찾을 수 없습니다."));
        var previous = tokens.findResetForUpdate(member.id).orElse(null);
        if (previous != null && previous.issuedAt.plusSeconds(60).isAfter(Instant.now())) throw new ApiException(429, "1분 후 다시 요청해 주세요.");
        String code = "%06d".formatted(random.nextInt(1000000));
        tokens.deleteReset(member.id);
        tokens.upsertReset(member.id, code, Instant.now());
        return Map.of("userId", member.id, "email", member.email, "devResetCode", code);
    }
    /** Failed code attempts must persist, so ApiException does not roll this transaction back. */
    @Transactional(noRollbackFor = ApiException.class)
    public void reset(Reset form) {
        var userId = form.userId().trim();
        var reset = tokens.findResetForUpdate(userId).orElse(null);
        if (reset == null || reset.issuedAt.plusSeconds(600).isBefore(Instant.now()) || reset.attempts >= 5)
            throw new ApiException(422, "재설정 요청이 만료되었습니다. 비밀번호 찾기를 다시 진행해 주세요.");
        tokens.increaseResetAttempts(userId);
        if (!MessageDigest.isEqual(form.code().getBytes(StandardCharsets.UTF_8), reset.code.getBytes(StandardCharsets.UTF_8)))
            throw new ApiException(422, "인증 코드가 일치하지 않습니다.");
        AuthService.password(form.password());
        var member = auth.members.findLockedById(userId).orElseThrow(() -> new ApiException(404, "회원을 찾을 수 없습니다."));
        member.passwordHash = auth.encoder.encode(form.password()); member.credentialVersion++;
        auth.members.update(member);
        tokens.deleteRefreshByMember(userId);
        tokens.deleteReset(userId);
    }
}
