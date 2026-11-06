package com.enjoytrip;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PostingSetOperations {
    public Set<Long> intersect(Collection<? extends Set<Long>> input) {
        var sets = new ArrayList<Set<Long>>(input);
        if (sets.isEmpty()) return Set.of();
        sets.sort(Comparator.comparingInt(Set::size));
        Set<Long> result = new HashSet<>(sets.get(0));
        for (int i = 1; i < sets.size() && !result.isEmpty(); i++) result.retainAll(sets.get(i));
        return result;
    }
}
