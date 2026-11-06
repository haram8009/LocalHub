package com.enjoytrip;

import java.util.*;
import org.springframework.stereotype.Component;

/** Pure Jaccard + two-row LCS; no HTTP or persistence dependencies. */
@Component
public class CourseSimilarityCalculator {
    public record Similarity(double score, int commonPlaceCount, int lcsLength,
                             double placeSimilarity, double orderSimilarity) {}

    public Similarity calculate(List<String> first, List<String> second) {
        // Legacy duplicated stops count once, preserving their first visit order.
        List<String> a = distinctStops(first), b = distinctStops(second);
        if (a.isEmpty() || b.isEmpty()) return new Similarity(0, 0, 0, 0, 0);
        Set<String> aSet = new HashSet<>(a), bSet = new HashSet<>(b);
        int common = 0;
        for (String id : aSet) if (bSet.contains(id)) common++;
        double jaccard = (double) common / (aSet.size() + bSet.size() - common);
        int lcs = lcsLength(a, b);
        double order = (double) lcs / Math.max(a.size(), b.size());
        return new Similarity(0.6 * jaccard + 0.4 * order, common, lcs, jaccard, order);
    }

    static List<String> distinctStops(List<String> input) {
        if (input == null) return List.of();
        return input.stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().toList();
    }

    public int lcsLength(List<String> a, List<String> b) {
        if (a.size() < b.size()) return lcsLength(b, a);
        int[] previous = new int[b.size() + 1], current = new int[b.size() + 1];
        for (String first : a) {
            current[0] = 0;
            for (int j = 1; j <= b.size(); j++) {
                current[j] = Objects.equals(first, b.get(j - 1))
                    ? previous[j - 1] + 1 : Math.max(previous[j], current[j - 1]);
            }
            int[] swap = previous; previous = current; current = swap;
        }
        return previous[b.size()];
    }
}
