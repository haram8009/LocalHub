package com.enjoytrip;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new MigratingPasswordEncoder(); }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        // Authentication is checked against the server session by AuthService.
        // CSRF remains enabled, including login and anonymous POST requests.
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .formLogin(f -> f.disable()).httpBasic(b -> b.disable()).logout(l -> l.disable())
            .exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) -> {
                res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"message\":\"보안 토큰이 만료되었습니다. 새로고침 후 다시 시도해 주세요.\"}");
            })).build();
    }
}
