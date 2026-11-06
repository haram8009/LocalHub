import com.enjoytrip.CourseSimilarityCalculator;
import java.util.*;

/** Pure calculation + sorting benchmark; excludes SQL, HTTP and payload serialization. */
public class CourseSimilarityBenchmark {
    record Scored(int id, double score) {}
    static final CourseSimilarityCalculator CALCULATOR = new CourseSimilarityCalculator();
    static long evaluate(List<String> base, List<List<String>> routes) {
        var scores = new ArrayList<Scored>();
        var places = new HashSet<>(base);
        for (int i = 0; i < routes.size(); i++) {
            var route = routes.get(i);
            if (route.stream().noneMatch(places::contains)) continue;
            scores.add(new Scored(i, CALCULATOR.calculate(base, route).score()));
        }
        scores.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparingInt(Scored::id));
        return scores.size();
    }
    public static void main(String[] args) {
        System.out.println("routes,length,candidates,median_ms,p95_ms");
        for (int n : new int[]{100, 1000, 10000}) for (int length : new int[]{3, 10, 30}) {
            List<String> base = java.util.stream.IntStream.range(0, length).mapToObj(String::valueOf).toList();
            var routes = new ArrayList<List<String>>(); Random random = new Random(42);
            for (int i = 0; i < n; i++) {
                var route = new ArrayList<String>();
                for (int j = 0; j < length; j++) route.add(String.valueOf(i % 2 == 0 ? j : 100 + j));
                Collections.shuffle(route, random); routes.add(route);
            }
            for (int i = 0; i < 8; i++) evaluate(base, routes);
            long[] samples = new long[21]; long candidates = 0;
            for (int i = 0; i < samples.length; i++) { long start = System.nanoTime(); candidates = evaluate(base, routes); samples[i] = System.nanoTime() - start; }
            Arrays.sort(samples);
            System.out.printf(Locale.ROOT, "%d,%d,%d,%.4f,%.4f%n", n, length, candidates, samples[10] / 1e6, samples[19] / 1e6);
        }
    }
}
