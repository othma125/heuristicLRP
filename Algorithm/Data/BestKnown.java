// Author: Othmane

package Algorithm.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The best known costs published for the LRPLib instances, read once from
 * {@code Algorithm/LRPLib/bks.csv}. A missing file leaves every instance
 * unknown rather than failing the run.
 *
 * @author Othmane EL YAAKOUBI
 */
public final class BestKnown {

    private static final String FILE = "Algorithm/LRPLib/bks.csv";
    // The Barreto files are named after their source instance, the published table after the
    // paper and the instance size; the customer counts pair them up, and the two 32x5 files
    // are told apart by their depot capacity (8000 first, 11000 second).
    private static final Map<String, String> BARRETO_NAMES = Map.ofEntries(
            Map.entry("christ50", "christofides69-50x5"),
            Map.entry("christ75", "christofides69-75x10"),
            Map.entry("christ100", "christofides69-100x10"),
            Map.entry("das88", "daskin95-88x8"),
            Map.entry("das150", "daskin95-150x10"),
            Map.entry("gaspelle", "gaskell67-21x5"),
            Map.entry("gaspelle2", "gaskell67-22x5"),
            Map.entry("gaspelle3", "gaskell67-29x5"),
            Map.entry("gaspelle4", "gaskell67-32x5-1"),
            Map.entry("gaspelle5", "gaskell67-32x5-2"),
            Map.entry("gaspelle6", "gaskell67-36x5"),
            Map.entry("min27", "min92-27x5"),
            Map.entry("min134", "min92-134x8"));
    private static Map<String, Double> Costs;

    private BestKnown() {
        // Static accessors only.
    }

    /**
     * @param instance an instance name or file name, with or without its
     *                 {@code coord} prefix and {@code .dat} extension
     * @return the best known cost, or {@link Double#NaN} when the instance is
     *         not listed
     */
    public static synchronized double of(String instance) {
        if (Costs == null)
            Costs = read();
        String key = instance.toLowerCase(Locale.ROOT)
                             .replaceFirst("^coord", "")
                             .replaceFirst("\\.dat$", "");
        key = BARRETO_NAMES.getOrDefault(key, key);
        // The Tuzun files carry a P the published table drops.
        return Costs.getOrDefault(key, Costs.getOrDefault(key.replaceFirst("^p", ""), Double.NaN));
    }

    /**
     * @return best known cost per lower-cased instance name
     */
    private static Map<String, Double> read() {
        Map<String, Double> costs = new HashMap<>();
        try (Stream<String> lines = Files.lines(Path.of(FILE))) {
            lines.skip(1)
                 .map(line -> line.split(","))
                 .filter(column -> column.length > 2)
                 .forEach(column -> costs.put(column[1].toLowerCase(Locale.ROOT), Double.valueOf(column[2])));
        } catch (IOException e) {
            System.err.println("No best known costs read from " + FILE + ": " + e.getMessage());
        }
        return costs;
    }
}
