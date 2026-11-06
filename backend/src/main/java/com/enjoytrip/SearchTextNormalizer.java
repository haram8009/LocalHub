package com.enjoytrip;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SearchTextNormalizer {
    private static final Pattern BOUNDARY = Pattern.compile("[\\s\\p{Z}\\p{P}\\p{S}]+", Pattern.UNICODE_CHARACTER_CLASS);
    public List<String> tokens(String text) {
        if (text == null) return List.of();
        String value = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return Arrays.stream(BOUNDARY.split(value)).filter(s -> !s.isBlank()).distinct().toList();
    }
    static String containsPattern(String query) {
        return "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
