// Author: Othmane

package Algorithm.Data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * LRPLIB instance parser and distance provider.
 *
 * <p>Reads the {@code .dat} format described in {@code Algorithm/LRPLib/files
 * format.txt}: number of customers, number of depots, depot coordinates,
 * customer coordinates, vehicle capacity, depot capacities, customer demands,
 * depot opening costs, route opening cost, and a flag telling whether costs are
 * real (1) or integer (0). The file is a flat stream of whitespace-separated
 * numbers, so it is read positionally.
 *
 * <p>Distances are Euclidean. On integer instances (flag 0) they are multiplied
 * by 100 and rounded up, which is what reproduces the benchmark's published costs.
 *
 * <p>Depots and customers are both 0-based in the public accessors; internally
 * they share one coordinate space with the depots first, matching the file.
 *
 * @author Othmane EL YAAKOUBI
 */
public class InputData {
    public final String FileName;
    private final int CustomerNumber;
    private final int DepotNumber;
    private final int Capacity;
    private final int[] Demands;
    private final Depot[] Depots;
    private final double RouteCost;
    private final boolean RealCosts;
    // ponytail: full matrix instead of a lazy cache; LRPLib tops out at 220 nodes (~380 KB),
    // which stays in cache. If instances ever get much larger, drop the matrix and compute
    // each distance on the fly — do NOT reach for a concurrent Edge cache. Measured on
    // heuristicCVRP, an Edge-keyed ConcurrentHashMap costs 22/49/72 ns per lookup at n =
    // 101/1001/10001 (it degrades as it stops fitting in cache) against 5.5/5.4/13.5 ns for
    // plain recomputation: memoizing a sqrt is slower than the sqrt.
    private final double[][] Distances;
    // Carried here because the instance is the one object every split and local search
    // already receives, so a stop can be seen deep in the search without new plumbing.
    private volatile boolean StopRequested = false;

    /** Asks any split work running on this instance to abort as soon as it can. */
    public void requestStop() {
        this.StopRequested = true;
    }

    /**
     * @return {@code true} once {@link #requestStop()} has been called
     */
    public boolean isStopRequested() {
        return this.StopRequested;
    }

    /**
     * Parses an LRPLIB {@code .dat} instance from disk.
     *
     * @param file path to the {@code .dat} file
     * @throws IOException if the file cannot be read
     */
    public InputData(String file) throws IOException {
        this.FileName = file;
        String[] token = Files.readString(Path.of(file), StandardCharsets.ISO_8859_1)
                              .trim().split("\\s+");
        int t = 0;

        /* ---------- SIZES ---------- */
        this.CustomerNumber = (int) Double.parseDouble(token[t++]);
        this.DepotNumber = (int) Double.parseDouble(token[t++]);
        int nodes = this.DepotNumber + this.CustomerNumber;

        /* ---------- COORDINATES (depots first, then customers) ---------- */
        Location[] locations = new Location[nodes];
        for (int n = 0; n < nodes; n++)
            locations[n] = new Location(Double.parseDouble(token[t++]),
                                        Double.parseDouble(token[t++]));

        /* ---------- CAPACITIES, DEMANDS, COSTS ---------- */
        this.Capacity = (int) Double.parseDouble(token[t++]);
        int[] depotCapacities = new int[this.DepotNumber];
        for (int d = 0; d < this.DepotNumber; d++)
            depotCapacities[d] = (int) Double.parseDouble(token[t++]);
        this.Demands = new int[this.CustomerNumber];
        for (int c = 0; c < this.CustomerNumber; c++)
            this.Demands[c] = (int) Double.parseDouble(token[t++]);
        this.Depots = new Depot[this.DepotNumber];
        for (int d = 0; d < this.DepotNumber; d++)
            this.Depots[d] = new Depot(d, locations[d], depotCapacities[d],
                                       Double.parseDouble(token[t++]));
        this.RouteCost = Double.parseDouble(token[t++]);
        this.RealCosts = Double.parseDouble(token[t++]) == 1;
        // Positional parsing silently shifts on a file that does not follow the format,
        // so refuse anything whose number count does not match the announced sizes.
        if (t != token.length)
            throw new IOException(file + ": expected " + t + " numbers, found " + token.length);

        /* ---------- DISTANCES ---------- */
        this.Distances = new double[nodes][nodes];
        for (int a = 0; a < nodes; a++)
            for (int b = 0; b < a; b++) {
                double distance = locations[a].distanceTo(locations[b]);
                // files format.txt says the scaled distance is truncated, but the published
                // costs only reproduce with each leg rounded up: on 20-5-2b the optimum comes
                // out at exactly 37542 this way, and 21 below it when truncated.
                if (!this.RealCosts)
                    distance = Math.ceil(distance * 100);
                this.Distances[a][b] = this.Distances[b][a] = distance;
            }
    }

    /* ======================
       Distance access
       ====================== */
    /**
     * @param stop1 first 0-based customer index
     * @param stop2 second 0-based customer index
     * @return the distance between the two customers
     */
    public double getTwoStopsDistance(int stop1, int stop2) {
        return this.Distances[this.DepotNumber + stop1][this.DepotNumber + stop2];
    }

    /**
     * @param stop  0-based customer index
     * @param depot the depot the route returns to
     * @return the distance from the customer back to the depot
     */
    public double getStopToDepotDistance(int stop, Depot depot) {
        return this.Distances[this.DepotNumber + stop][depot.index()];
    }

    /**
     * @param depot the depot the route leaves from
     * @param stop  0-based customer index
     * @return the distance from the depot out to the customer
     */
    public double getDepotToStopDistance(Depot depot, int stop) {
        return this.Distances[depot.index()][this.DepotNumber + stop];
    }

    /* ======================
       Getters
       ====================== */
    /**
     * @return the number of customers
     */
    public int getCustomerNumber() {
        return this.CustomerNumber;
    }

    /**
     * @return the number of candidate depots
     */
    public int getDepotNumber() {
        return this.DepotNumber;
    }

    /**
     * @return the number of nodes in the instance, depots plus customers
     */
    public int getSize() {
        return this.DepotNumber + this.CustomerNumber;
    }

    /**
     * @return the vehicle capacity
     */
    public int getCapacity() {
        return this.Capacity;
    }

    /**
     * @param stop 0-based customer index
     * @return the demand of the customer
     */
    public int getDemand(int stop) {
        return this.Demands[stop];
    }

    /**
     * @return the candidate depots, with their coordinates, capacities and
     *         opening costs (the backing array, not a copy)
     */
    public Depot[] getDepots() {
        return this.Depots;
    }

    /**
     * @return the opening cost of a single route (one vehicle)
     */
    public double getRouteCost() {
        return this.RouteCost;
    }

    /**
     * @return {@code true} when the instance uses real costs, {@code false} when
     *         distances are scaled by 100 and rounded up to integers
     */
    public boolean hasRealCosts() {
        return this.RealCosts;
    }

    /* ======================
       toString
       ====================== */

    @Override
    public String toString() {
        return "InputData { CustomerNumber = " + this.CustomerNumber
                + ", DepotNumber = " + this.DepotNumber
                + ", Capacity = " + this.Capacity
                + ", RouteCost = " + this.RouteCost
                + ", RealCosts = " + this.RealCosts + " }";
    }

    // ponytail: self-check instead of a test framework, run with
    // `java -cp out Algorithm.Data.InputData` from the project root.
    public static void main(String[] args) throws IOException {
        InputData data = new InputData("Algorithm/LRPLib/Instances_Prodhon_LRP/coord20-5-1.dat");
        Depot[] depots = data.getDepots();
        double depot0ToStop0 = Math.ceil(Math.hypot(20 - 6, 35 - 7) * 100); // 3131
        if (data.getCustomerNumber() != 20 || data.getDepotNumber() != 5
                || data.getCapacity() != 70 || depots[4].capacity() != 140
                || data.getDemand(0) != 17 || data.getDemand(19) != 16
                || depots[0].openingCost() != 10841 || depots[4].openingCost() != 7497
                || !depots[0].location().equals(new Location(6, 7))
                || data.getRouteCost() != 1000 || data.hasRealCosts()
                || data.getDepotToStopDistance(depots[0], 0) != depot0ToStop0
                || data.getStopToDepotDistance(0, depots[0]) != depot0ToStop0)
            throw new AssertionError("LRP parsing is broken: " + data);
        System.out.println("ok " + data);
    }
}
