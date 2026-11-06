import java.util.*;
import java.util.concurrent.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** Optional local calibration. Uses a fixed synthetic password, never reads member data. */
public class PasswordCostBenchmark {
    interface Work { void run(); }
    static double median(Work work) {
        for (int i = 0; i < 4; i++) work.run();
        long[] samples = new long[15];
        for (int i = 0; i < samples.length; i++) { long start = System.nanoTime(); work.run(); samples[i] = System.nanoTime() - start; }
        Arrays.sort(samples); return samples[7] / 1e6;
    }
    public static void main(String[] args) throws Exception {
        System.out.println("memory_kib,iterations,encode_median_ms,verify_median_ms,concurrent4_wall_ms,jobs");
        for (int[] config : new int[][]{{19456, 2}, {19456, 3}, {32768, 2}}) {
            var encoder = new Argon2PasswordEncoder(16, 32, 1, config[0], config[1]);
            String hash = encoder.encode("SyntheticTrip123!");
            double encode = median(() -> encoder.encode("SyntheticTrip123!"));
            double verify = median(() -> { if (!encoder.matches("SyntheticTrip123!", hash)) throw new AssertionError(); });
            try (var pool = Executors.newFixedThreadPool(4)) {
                List<Callable<Boolean>> jobs = new ArrayList<>();
                for (int i = 0; i < 16; i++) jobs.add(() -> encoder.matches("SyntheticTrip123!", hash));
                long start = System.nanoTime();
                for (var result : pool.invokeAll(jobs)) if (!result.get()) throw new AssertionError();
                System.out.printf(Locale.ROOT, "%d,%d,%.4f,%.4f,%.4f,16%n", config[0], config[1], encode, verify, (System.nanoTime() - start) / 1e6);
            }
        }
    }
}
