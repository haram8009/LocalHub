package com.enjoytrip;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static com.enjoytrip.AuthRequests.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
 private final AccountService service;
 public AuthController(AccountService service) { this.service=service; }
 @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
 @GetMapping("/me") public AuthService.User me(HttpSession session) { return service.me(session); }
 @PostMapping("/signup") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public AuthService.User signup(@Valid @RequestBody Signup form) { return service.signup(form); }
 @PostMapping("/login") public AuthService.User login(@Valid @RequestBody Login form,HttpServletRequest request) { return service.login(form,request); }
 @PostMapping("/logout") public void logout(HttpSession session) { service.logout(session); }
 @PutMapping("/profile") public AuthService.User profile(@Valid @RequestBody Profile form,HttpSession session) { return service.profile(form,session); }
 @PutMapping("/password") public void password(@Valid @RequestBody Passwords form,HttpSession session) { service.password(form,session); }
 @DeleteMapping("/me") public void withdraw(@Valid @RequestBody Password form,HttpSession session) { service.withdraw(form,session); }
 @PostMapping("/find-id") public Map<String,Object> findId(@Valid @RequestBody FindId form) { return service.findId(form); }
 @PostMapping("/reset-target") public Map<String,String> resetTarget(@Valid @RequestBody Target form,HttpSession session) { return service.resetTarget(form,session); }
 @PostMapping("/reset-password") public void reset(@Valid @RequestBody Reset form,HttpSession session) { service.reset(form,session); }
}
