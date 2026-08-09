// Author: Othmane

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import Algorithm.Data.BestKnown;
import Algorithm.Data.InputData;
import Algorithm.Metaheuristics.GeneticAlgorithm;
import Algorithm.Metaheuristics.MetaHeuristic;
import Algorithm.Solution.GiantTour;



/**
 * Batch entry point: solves every {@code .dat} instance in an LRPLIB benchmark
 * directory (in ascending size order), looks up each instance's best known cost
 * in {@code Algorithm/LRPLib/bks.csv}, and writes the results with the gap to a
 * CSV report.
 *
 * @author Othmane EL YAAKOUBI
 */
public class benchmark {

    /**
     * @param args the command line arguments (unused)
     */
    public static void main(String[] args) {

        String benchmarkDirPath = "Algorithm/LRPLib/Instances_Prodhon_LRP";
//        String benchmarkDirPath = "Algorithm/LRPLib/Instances_Tuzun_LRP";
//        String benchmarkDirPath = "Algorithm/LRPLib/Instances_Barreto_LRP";

        File dir = new File(benchmarkDirPath);
        File[] files = dir.listFiles();
        if (files == null) {
            System.err.println("Directory not found or empty: " + dir.getAbsolutePath());
            return;
        }
        // Output CSV
        String outputFile = "results " + benchmarkDirPath.replace("/", ".") +".csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Header
            writer.println("File Name,Customers,Depots,Best Solution Reach Time(ms),Cost Value,Known Best,Gap(%)");

            // Build and sort by customer count
            Map<String, InputData> datasets = Arrays.stream(files)
                                                    .filter(file -> file.getName().endsWith(".dat"))
                                                    .parallel()
                                                    .map(file -> {
                                                        try {
                                                            return new InputData(benchmarkDirPath + "//" + file.getName());
                                                        } catch (IOException e) {
                                                            System.err.println("Error reading file: " + file.getAbsolutePath());
                                                            return null;
                                                        }
                                                    })
                                                    .filter(data -> data != null)
                                                    .collect(Collectors.toMap(data -> instanceName(data.FileName), data -> data));

            // Process in sorted order
            datasets.entrySet()
                    .stream()
                    .sorted(Comparator.comparingInt(entry -> entry.getValue().getCustomerNumber()))
                    .forEach(entry -> {
                        InputData data = entry.getValue();
                        MetaHeuristic algorithm = new GeneticAlgorithm(data);
                        algorithm.Run();
                        if (algorithm.isFeasible()) {
                            GiantTour gt = algorithm.getBestGiantTour();
                            System.out.println(gt);
                            try {
                                gt.export(data);
                            } catch (IOException e) {
                                System.err.println("Error exporting solution: " + e.getMessage());
                            }
                            System.out.println("\nEnd Time = " + algorithm.getRunningTime() + " ms\n");

                            // Print/display solution
                            long end_time = algorithm.getRunningTime();
                            // Lookup best known
                            double best = BestKnown.of(entry.getKey());

                            // Compute gap
                            String gapStr = "NA";
                            if (!Double.isNaN(best)) {
                                double gap = (gt.getFitness() - best) / best;
                                gapStr = String.format(Locale.US, "%.2f", gap  * 100d);
                            }
                            // Write result to CSV
                            writer.printf(Locale.US, "%s,%s,%s,%s,%.2f,%s,%s\n", entry.getKey(), data.getCustomerNumber(), data.getDepotNumber(), end_time, gt.getFitness(), Double.toString(best), gapStr);
                        }
                        else {
                            System.out.println("No feasible solution found for " + data.FileName);
                            System.out.println();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error writing results: " + e.getMessage());
        }
        System.out.println("All results stored in \"" + outputFile + "\"");
    }

    /**
     * @param file path to an instance file
     * @return the instance name, i.e. the file name without its {@code coord}
     *         prefix and {@code .dat} extension
     */
    private static String instanceName(String file) {
        return new File(file).getName().replaceFirst("^coord", "").replaceFirst("\\.dat$", "");
    }
}
