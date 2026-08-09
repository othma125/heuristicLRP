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
