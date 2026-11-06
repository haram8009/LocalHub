package com.enjoytrip;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CourseSimilarityCalculatorTest {
    final CourseSimilarityCalculator calculator = new CourseSimilarityCalculator();
    @Test void verifiesProposalExamplesWithoutRoundingBeforeRanking() {
        var base = List.of("A", "B", "C", "D");
        var partial = calculator.calculate(base, List.of("A", "B", "E", "D"));
        assertThat(partial.commonPlaceCount()).isEqualTo(3);
        assertThat(partial.lcsLength()).isEqualTo(3);
        assertThat(partial.placeSimilarity()).isCloseTo(0.6, within(1e-12));
        assertThat(partial.orderSimilarity()).isCloseTo(0.75, within(1e-12));
        assertThat(partial.score()).isCloseTo(0.66, within(1e-12));
        var reverse = calculator.calculate(base, List.of("D", "C", "B", "A"));
        assertThat(reverse.score()).isCloseTo(0.7, within(1e-12));
        assertThat(reverse.score()).isGreaterThan(partial.score());
    }
    @Test void handlesIdenticalDisjointEmptyAndLegacyDuplicates() {
        assertThat(calculator.calculate(List.of("A", "B"), List.of("A", "B")).score()).isEqualTo(1);
        assertThat(calculator.calculate(List.of("A"), List.of("B")).score()).isZero();
        assertThat(calculator.calculate(List.of(), List.of("A")).score()).isZero();
        assertThat(calculator.calculate(Arrays.asList("A", null, "", "A", "B"), List.of("A", "B")).score()).isEqualTo(1);
    }
    @Test void twoRowLcsAgreesWithExhaustiveSubsequenceReference() {
        Random random = new Random(42);
        for (int sample = 0; sample < 500; sample++) {
            List<String> a = random.ints(random.nextInt(8), 0, 4).mapToObj(String::valueOf).toList();
            List<String> b = random.ints(random.nextInt(8), 0, 4).mapToObj(String::valueOf).toList();
            int expected = 0;
            for (int mask = 0; mask < (1 << a.size()); mask++) {
                var subsequence = new ArrayList<String>();
                for (int i = 0; i < a.size(); i++) if ((mask & (1 << i)) != 0) subsequence.add(a.get(i));
                int matched = 0;
                for (String value : b) if (matched < subsequence.size() && subsequence.get(matched).equals(value)) matched++;
                if (matched == subsequence.size()) expected = Math.max(expected, matched);
            }
            assertThat(calculator.lcsLength(a, b)).as("a=%s b=%s", a, b).isEqualTo(expected);
        }
    }
}
