package com.enjoytrip;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SearchAlgorithmsTest {
    @Test void normalizesCaseWidthWhitespaceAndPunctuationWithoutJoiningWords() {
        var normalizer = new SearchTextNormalizer();
        assertThat(normalizer.tokens(" ＴＲＩＰ, 부산\u3000바다 / 부산! ")).containsExactly("trip", "부산", "바다");
        assertThat(normalizer.tokens("부산에서 바다를")).containsExactly("부산에서", "바다를");
        assertThat(normalizer.tokens("% _ + !!")).isEmpty();
        assertThat(SearchTextNormalizer.containsPattern("a%b_c\\d")).isEqualTo("%a\\%b\\_c\\\\d%");
    }
    @Test void setOperationsHandleMissingTermsAndDoNotMutatePostingSets() {
        var operations = new PostingSetOperations();
        Set<Long> a = new HashSet<>(Set.of(1L, 4L, 8L)), b = new HashSet<>(Set.of(1L, 3L, 8L));
        assertThat(operations.intersect(List.of(a, b))).containsExactlyInAnyOrder(1L, 8L);
        assertThat(operations.intersect(List.of(a, Set.of()))).isEmpty();
        assertThat(operations.intersect(List.of())).isEmpty();
        assertThat(a).containsExactlyInAnyOrder(1L, 4L, 8L);
    }
}
