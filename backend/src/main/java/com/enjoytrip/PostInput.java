package com.enjoytrip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record PostInput(@NotBlank @Pattern(regexp="COURSE|REVIEW|TIP|RECOMMENDATION") String category,
                 @NotBlank @Size(max=200) String title, @NotBlank @Size(max=20000) String content,
                 @NotNull @Size(max=32) List<@NotNull @Valid PostLocation> locations) {}
