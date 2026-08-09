// Author: Othmane

package Web.server;

import Algorithm.Data.BestKnown;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only view of the LRPLib dataset under {@code Algorithm/LRPLib}: folder
 * and instance listing, instance path resolution, and the best known cost
 * published for an instance.
 *
 * @author Othmane EL YAAKOUBI
 */
final class Instances {

    private static final File DIR = new File("Algorithm/LRPLib");
    private static final String DAT_EXT = ".dat";

    private Instances() {
        // Static accessors only.
    }

    /**
     * Lists the LRPLib subdirectories, one per benchmark.
     *
     * @return the sorted folder names, empty when the dataset is absent
     */
    static List<String> folders() {
        File[] dirs = DIR.listFiles(File::isDirectory);
        return dirs == null ? List.of()
                : Arrays.stream(dirs).map(File::getName).sorted().collect(Collectors.toList());
    }

    /**
     * Lists the {@code .dat} instances of a folder, without the extension.
     *
     * @param folder the folder name, as received from the client
     * @return the sorted instance names, empty when the folder is unknown
     */
    static List<String> in(String folder) {
        String safe = safeName(folder);
        if (safe == null)
            return List.of();
        String[] files = new File(DIR, safe).list((d, n) -> n.endsWith(DAT_EXT));
        return files == null ? List.of()
                : Arrays.stream(files).map(n -> n.substring(0, n.length() - DAT_EXT.length()))
                        .sorted().collect(Collectors.toList());
    }

    /**
     * Resolves the {@code folder} and {@code file} query parameters into a
     * {@code .dat} file.
     *
     * @param query the parsed query parameters
     * @return the instance file, or {@code null} when the parameters are missing or unsafe
     */
    static File datFile(Map<String, String> query) {
        String folder = safeName(query.get("folder"));
        String file = safeName(query.get("file"));
        if (folder == null || file == null)
            return null;
        return new File(new File(DIR, folder), file + DAT_EXT);
    }

    /**
     * @param dat the instance file
     * @return the best known cost published for the instance, or {@code NaN}
     *         when the benchmark does not list one
     */
    static double bestKnownOf(File dat) {
        return BestKnown.of(dat.getName());
    }

    /** Trust boundary: reject anything but a plain file/dir name (no traversal). */
    private static String safeName(String name) {
        if (name == null || name.isEmpty() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return null;
        }
        return name;
    }
}
