package com.enjoytrip;
import jakarta.validation.constraints.*;
public final class AuthRequests {
 private AuthRequests() {}
    public record Signup(@Pattern(regexp="[a-z0-9]{4,20}", message="아이디는 영문 소문자와 숫자 4~20자입니다.") @NotNull String userId,
                  @NotNull String password, @NotBlank @Size(min=2,max=20) String name,
                  @NotBlank @Email @Size(max=100) String email) {}
    public record Login(@NotBlank String userId, @NotBlank String password) {}
    public record Profile(@NotBlank @Size(min=2,max=20) String name, @NotBlank @Email @Size(max=100) String email) {}
    public record FindId(@NotBlank String name, @NotBlank @Email String email) {}
    public record Target(@NotBlank String userId, @NotBlank @Email String email) {}
    public record Reset(@NotBlank String code, @NotBlank String password) {}
    public record Passwords(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    public record Password(@NotBlank String password) {}

}
