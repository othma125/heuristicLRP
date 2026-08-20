// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.InputData;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

/**
 * The chromosome of the genetic algorithm: a permutation of all customers with
 * no depot markers (a "giant tour"). Feasible vehicle routes are obtained on
 * demand by the graph-based split procedure ({@link AuxiliaryGraph}), whose
 * shortest path defines the optimal partition of the tour into routes. Fitness
 * is the cost of that split; giant tours are comparable by fitness.
 *
 * @author Othmane EL YAAKOUBI
 */
public class GiantTour implements Comparable<GiantTour>, AutoCloseable {

    public int[] Sequence;
    public AuxiliaryGraph AuxiliaryGraph = null;

    /**
     * Creates a random giant tour and optionally splits it into routes.
     *
     * @param data  the problem instance
     * @param split if {@code true}, the tour is immediately split into routes
     */
    public GiantTour(InputData data, boolean split) {
        this.setRandomGiantTour(data);
        if (split)
            this.Split(data);
    }

    /**
     * Creates a random giant tour and immediately splits it into routes.
     *
     * @param data the problem instance
     */
    public GiantTour(InputData data) {
        this(data, true);
    }

    /**
     * Creates a giant tour from a given sequence.
     *
     * @param seq the customer sequence to use
     */
    public GiantTour(int[] seq) {
        this.Sequence = seq;
    }

    /**
     * Graph-based crossover: builds a giant tour by combining the given parent
     * tours through the auxiliary graph, keeping the best subsequences bounded
     * by the parents' fitness. The result is only feasible if the combined
     * graph yields a complete split.
     *
     * @param data        the problem instance
     * @param giant_tours the parent tours to recombine
     */
    public GiantTour(InputData data, GiantTour ... giant_tours) {
        double bound = Double.NEGATIVE_INFINITY;
        for (GiantTour gt : giant_tours) 
            if (gt.isFeasible() && gt.getFitness() > bound) 
                bound = gt.getFitness();
        AuxiliaryGraph graph = new AuxiliaryGraph(data, bound, giant_tours);
        if (graph.isFeasible()) {
            this.AuxiliaryGraph = graph;
            this.Sequence = this.AuxiliaryGraph.getNewSequence(data);
        }
        else {
            // an infeasible child still has to be a usable parent: keep a sequence
            this.Sequence = giant_tours[0].Sequence.clone();
            graph.close();
        }
    }
    
    /**
     * Re-runs the split procedure on the current sequence, using the current
     * fitness as the pruning bound.
     *
     * @param data the problem instance
     * @return {@code true} if the re-split improved on the current fitness
     */
    public boolean Split(InputData data) {
        return this.Split(data, this.getFitness());
    }

    /**
     * Splits the giant tour into routes via the auxiliary graph. A graph that
     * does not beat {@code bound} is discarded; when the tour already holds a
     * graph, each of its candidate solutions is re-split and the best result
     * strictly under {@code bound} replaces it.
     *
     * @param data  the problem instance
     * @param bound cost upper bound used to prune the graph
     * @return {@code true} if a split beating {@code bound} was accepted, i.e.
     *         the tour now holds a strictly better graph than it did
     */
    private boolean Split(InputData data, double bound) {
        boolean c = false;
        if (this.AuxiliaryGraph == null) {
            AuxiliaryGraph graph = new AuxiliaryGraph(data, bound, this);
            // getLabel() is infinite on an infeasible graph, so this one test covers
            // both "feasible" and "better than what we had".
            if (graph.getLabel() < bound) {
                c = true;
                this.AuxiliaryGraph = graph;
            }
            else
                graph.close();
        }
        else {
            var feasibleTours = this.AuxiliaryGraph.getLastNode()
                                                    .getParetoSet()
                                                    .stream()
                                                    .map(solution -> new GiantTour(solution.getNewSequence()))
                                                    .filter(gt -> gt.Split(data, bound))
                                                    .collect(Collectors.toList());
            GiantTour best = feasibleTours.stream()
                                          .min(Comparator.comparingDouble(GiantTour::getFitness))
                                          .orElse(null);
            for (GiantTour gt : feasibleTours) 
                if (gt != best)
                    gt.close();
            if (best != null) {
                c = true;
                this.Sequence = best.Sequence;
                this.AuxiliaryGraph.close();
                this.AuxiliaryGraph = best.AuxiliaryGraph;
            }
            feasibleTours.clear();
        }
        return c;
    }

    /**
     * Initialises the sequence from clusters packed by randomized Best-Fit
     * Decreasing: customers are taken in decreasing demand order (equal demands
     * in random order) and each is placed in one of the two fullest clusters
     * that still fit it, systematically minimising the empty capacity per
     * cluster. The clusters are then concatenated in random order, each shuffled
     * internally, so the packing does not leak demand order into the sequence:
     * the split only needs each cluster's customers to be contiguous, and
     * keeping the order random preserves the population diversity the crossover
     * feeds on.
     *
     * <p>The bins are the candidate depots, not the vehicles as in the CVRP
     * version: there is one per depot and all are sized by the smallest depot
     * capacity, so what the packing makes possible is the depot assignment. The
     * split still cuts each cluster into vehicle-sized routes on its own.
     *
     * @param data the problem instance
     */
    private void setRandomGiantTour(InputData data) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int n = data.getCustomerNumber();
        int[] customers = IntStream.range(0, n).toArray();
        for (int i = n - 1; i > 0; i--)
            new Move(i, rnd.nextInt(i + 1)).Swap(customers);
        // stable sort after the shuffle: demand descending, equal demands in random order
        customers = Arrays.stream(customers)
                            .boxed()
                            .sorted(Comparator.comparingInt(data::getDemand).reversed())
                            .mapToInt(Integer::intValue)
                            .toArray();
        int depots = data.getDepotNumber();
        int capacity = Arrays.stream(data.getDepots()).mapToInt(depot -> depot.capacity()).min().orElse(Integer.MAX_VALUE);
        Map<Integer, Set<Integer>> clusters = new HashMap<>();
        int[] loads = new int[depots];
        for (int c : customers) {
            int demand = data.getDemand(c);
            // best fit: the two fullest clusters the customer fits in, picked at random
            int d1 = -1, d2 = -1;
            for (int d = 0; d < depots; d++)
                if (loads[d] + demand <= capacity) {
                    if (d1 < 0 || loads[d] > loads[d1]) {
                        d2 = d1;
                        d1 = d;
                    }
                    else if (d2 < 0 || loads[d] > loads[d2]) 
                        d2 = d;
                }
            if (d2 >= 0 && rnd.nextBoolean())
                d1 = d2;
            if (d1 < 0) {
                // nothing fits: overload the emptiest cluster, Split repairs
                d1 = 0;
                for (int d = 1; d < depots; d++)
                    if (loads[d] < loads[d1])
                        d1 = d;
            }
            clusters.computeIfAbsent(d1, x -> new HashSet<>()).add(c);
            loads[d1] += demand;
        }
        List<Set<Integer>> shuffled_clusters = new ArrayList<>(clusters.values());
        Collections.shuffle(shuffled_clusters, rnd);
        this.Sequence = shuffled_clusters.stream()
                                        .flatMap(cluster -> {
                                            List<Integer> stops = new ArrayList<>(cluster);
                                            Collections.shuffle(stops, rnd);
                                            return stops.stream();
                                        })
                                        .mapToInt(Integer::intValue)
                                        .toArray();
    }

    /**
     * @param i position in the sequence
     * @return the stop at the given position
     */
    public int getStop(int i) {
        return this.Sequence[i];
    }

    /**
     * @return the number of stops in the tour
     */
    public int getLength() {
        return this.Sequence.length;
    }

    @Override
    public String toString() {
        return this.AuxiliaryGraph.toString();
    }

    /**
     * @return the cost of the best split, or {@link Double#POSITIVE_INFINITY}
     *         if the tour has no feasible split
     */
    public double getFitness() {
        return this.isFeasible() ? this.AuxiliaryGraph.getLabel() : Double.POSITIVE_INFINITY;
    }

    /**
     * @return the number of routes in the best split
     */
    public int getRoutesCount() {
        return this.AuxiliaryGraph.getRoutesCount();
    }

    /**
     * @return {@code true} if the tour has a feasible split into routes
     */
    public boolean isFeasible() {
        return this.AuxiliaryGraph == null ? false : this.AuxiliaryGraph.isFeasible();
    }

    /**
     * Orders giant tours by ascending fitness (split cost).
     *
     * @param gt the giant tour to compare against
     * @return a negative value, zero or a positive value as this tour is
     *         fitter than, equal to, or worse than {@code gt}
     */
    @Override
    public int compareTo(GiantTour gt) {
        if (this == gt)
            return 0;
        return Double.compare(this.getFitness(), gt.getFitness());
    }

    /**
     * @return the route listing of the best split, or {@code "NULL"} if
     *         infeasible
     */
    private String export() {
        return this.AuxiliaryGraph == null ? "NULL" : this.AuxiliaryGraph.export();
    }

    /**
     * Writes the solution to {@code Output/<instance>/<instance> Cost = N.sol}
     * in route format followed by a {@code Cost} line.
     *
     * @param data the problem instance (used for the instance name)
     * @throws IOException if the output file cannot be written
     */
    public void export(InputData data) throws IOException {
        String instanceName = new File(data.FileName).getName().replaceFirst("\\.dat$", "");
        File baseDir = new File("Output");
        File instanceDir = new File(baseDir, instanceName);
        instanceDir.mkdirs();
        String fileName = "Instance = " + instanceName + " Cost = " + (int) this.getFitness() + ".sol";
        File outFile = new File(instanceDir, fileName);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
            bw.write(this.export());
            bw.newLine();
            bw.write("Cost " + String.format(Locale.US, "%.2f", this.getFitness()));
            bw.newLine();
        }
    }

    /**
     * Releases the giant tour by closing its auxiliary graph, if any, and
     * dropping the sequence.
     */
    @Override
    public void close() {
        if (this.AuxiliaryGraph != null)
            this.AuxiliaryGraph.close();
        this.Sequence = null;
    }
}