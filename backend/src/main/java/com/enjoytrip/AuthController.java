package com.enjoytrip;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;
import static com.enjoytrip.AuthRequests.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
 static final String REFRESH_COOKIE = "refreshToken";
 private final AccountService service;
 private final JwtProvider jwt;
 public AuthController(AccountService service, JwtProvider jwt) { this.service=service; this.jwt=jwt; }

 /** Login/refresh/password-change response. The refresh token travels only in an HttpOnly cookie. */
 record TokenResponse(String accessToken, String tokenType, long expiresIn, AuthService.User user) {}

 private TokenResponse respond(AccountService.Tokens tokens, HttpServletResponse response) {
  response.addHeader(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), Duration.ofSeconds(jwt.refreshExpirySeconds)).toString());
  return new TokenResponse(tokens.accessToken(), "Bearer", jwt.accessExpirySeconds, tokens.user());
 }
 private ResponseCookie cookie(String value, Duration maxAge) {
  return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(jwt.cookieSecure)
   .sameSite("Strict").path("/api/auth").maxAge(maxAge).build();
 }

 @GetMapping("/me") public AuthService.User me(@AuthenticationPrincipal AuthPrincipal principal) { return service.me(principal); }
 @PostMapping("/signup") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public AuthService.User signup(@Valid @RequestBody Signup form) { return service.signup(form); }
 @PostMapping("/login") public TokenResponse login(@Valid @RequestBody Login form,HttpServletResponse response) { return respond(service.login(form),response); }
 @PostMapping("/refresh")
 public TokenResponse refresh(@CookieValue(name=REFRESH_COOKIE,required=false) String refreshToken,HttpServletResponse response) {
  try { return respond(service.refresh(refreshToken),response); }
  catch (ApiException e) {
   if (e.status==401) response.addHeader(HttpHeaders.SET_COOKIE, cookie("",Duration.ZERO).toString());
   throw e;
  }
 }
 @PostMapping("/logout")
 public void logout(@CookieValue(name=REFRESH_COOKIE,required=false) String refreshToken,HttpServletResponse response) {
  service.logout(refreshToken);
  response.addHeader(HttpHeaders.SET_COOKIE, cookie("",Duration.ZERO).toString());
 }
 @PutMapping("/profile") public AuthService.User profile(@Valid @RequestBody Profile form,@AuthenticationPrincipal AuthPrincipal principal) { return service.profile(form,principal); }
 @PutMapping("/password") public TokenResponse password(@Valid @RequestBody Passwords form,@AuthenticationPrincipal AuthPrincipal principal,HttpServletResponse response) { return respond(service.password(form,principal),response); }
 @DeleteMapping("/me") public void withdraw(@Valid @RequestBody Password form,@AuthenticationPrincipal AuthPrincipal principal,HttpServletResponse response) {
  service.withdraw(form,principal);
  response.addHeader(HttpHeaders.SET_COOKIE, cookie("",Duration.ZERO).toString());
 }
 @PostMapping("/find-id") public Map<String,Object> findId(@Valid @RequestBody FindId form) { return service.findId(form); }
 @PostMapping("/reset-target") public Map<String,String> resetTarget(@Valid @RequestBody Target form) { return service.resetTarget(form); }
 @PostMapping("/reset-password") public void reset(@Valid @RequestBody Reset form) { service.reset(form); }
}
