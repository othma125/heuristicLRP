// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.Depot;
import Algorithm.Data.InputData;
import Algorithm.Solution.LSM.LocalSearchMove;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A complete LRP solution: the vehicle {@link Route}s grouped by the
 * {@link Depot} that serves them, together with the set of stops they cover and
 * their total travelled distance. Solutions are comparable by total distance,
 * and can be improved in place by inter-route local search. Instances are built
 * incrementally by the auxiliary graph while decoding a giant tour.
 *
 * @author Othmane EL YAAKOUBI
 */
public final class Solution implements Comparable<Solution>, AutoCloseable {

    // Restarting the scan after each accepted move recurses, and the improving chain runs
    // long enough to overflow the stack, so the number of passes is capped.
    // ponytail: recursion with a cap, turn it into a loop if the cap starts costing gains
    private static final int MIN_LOCAL_SEARCH_PASSES = 10;

    private final Map<Depot, List<Route>> Routes;
    // The demand each opened depot already ships, kept in step with the routes so that
    // checking whether a depot can take more stops stays a lookup instead of a scan.
    private final Map<Depot, Integer> DepotLoads;
    private final BitSet Stops;
    private double TotalDistance;

    /**
     * @param distance the initial total travelled distance
     * @param capacity the expected number of routes, used to size the backing map
     */
    Solution(double distance, int capacity) {
        this.TotalDistance = distance;
        this.Routes = new LinkedHashMap<>(capacity, 1f);
        this.DepotLoads = new LinkedHashMap<>(capacity, 1f);
        this.Stops = new BitSet();
    }

    /**
     * Improves the solution by first optimising each route internally, then
     * repeatedly applying the best available inter-route move until no further
     * improving move exists. Routes replaced by a move are swapped in and the
     * total distance is updated accordingly.
     *
     * <p>Each applied move is followed by another pass, up to
     * max(10, sqrt(routes count)) of them, since a move creates two new routes
     * the remaining ones may now combine with. The cap keeps the cost bounded:
     * the search is called on every solution of every Pareto set, so descending
     * all the way to a local optimum would starve the genetic loop of generations.
     *
     * @param data the problem instance providing distances and capacity
     */
    void InterRoutesLocalSearch(InputData data) {
        this.InterRoutesLocalSearch(data, Math.max(MIN_LOCAL_SEARCH_PASSES, (int) Math.sqrt(this.getRoutesCount())));
    }

    /**
     * @param data   the problem instance providing distances and capacity
     * @param passes the number of moves still allowed
     */
    private void InterRoutesLocalSearch(InputData data, int passes) {
        if (passes == 0)
            return;
        // The route order biases which improving move is found first, so shuffling spreads the
        // search over different pairs instead of always draining the first depot's routes.
        List<Route> routes = this.getRoutes();
        Collections.shuffle(routes, ThreadLocalRandom.current());
        for (Route r1 : routes) {
            for (Route r2 : routes)
                if (r1 != r2 && r1.getDepot().equals(r2.getDepot())) {
                    LocalSearchMove lsm = r1.getLSM(data, r2);
                    if (lsm != null) {
                        lsm.Perform(data);
                        this.remove(r1);
                        this.TotalDistance -= r1.getTraveledDistance();
                        this.remove(r2);
                        this.TotalDistance -= r2.getTraveledDistance();
                        if (lsm.getFirstRoute() != null) {
                            this.add(lsm.getFirstRoute());
                            this.TotalDistance += lsm.getFirstRoute().getTraveledDistance();
                        }
                        if (lsm.getSecondRoute() != null) {
                            this.add(lsm.getSecondRoute());
                            this.TotalDistance += lsm.getSecondRoute().getTraveledDistance();
                        }
                        this.InterRoutesLocalSearch(data, passes - 1);
                        return;
                    }
                }
        }
    }
    
    /**
     * @param stop a 0-based customer index
     * @return {@code true} if the stop is already served by this solution
     */
    boolean contains(int stop) {
        return this.Stops.get(stop);
    }

    /**
     * Adds a route to the solution, under the depot serving it, and registers
     * all of its stops as served.
     *
     * @param new_route the route to add
     */
    void add(Route new_route) {
        this.Routes.computeIfAbsent(new_route.getDepot(), depot -> new LinkedList<>()).add(new_route);
        this.DepotLoads.merge(new_route.getDepot(), new_route.getSumDemand(), Integer::sum);
        for (int stop : new_route.getSequence())
            this.Stops.set(stop);
    }

    /**
     * @param depot a candidate depot
     * @return the demand this solution already ships from that depot
     */
    int getDepotLoad(Depot depot) {
        return this.DepotLoads.getOrDefault(depot, 0);
    }

    /**
     * Removes a route from its depot, dropping the depot itself once it serves
     * no route any more.
     *
     * @param route the route to remove
     */
    void remove(Route route) {
        List<Route> routes = this.Routes.get(route.getDepot());
        if (routes != null && routes.remove(route)) {
            if (routes.isEmpty()) {
                this.Routes.remove(route.getDepot());
                this.DepotLoads.remove(route.getDepot());
            }
            else
                this.DepotLoads.merge(route.getDepot(), -route.getSumDemand(), Integer::sum);
        }
    }

    /**
     * @return the routes making up this solution, flattened across depots
     */
    List<Route> getRoutes() {
        List<Route> routes = new LinkedList<>();
        for (List<Route> depotRoutes : this.Routes.values())
            routes.addAll(depotRoutes);
        return routes;
    }

    /**
     * @param depot the depot to filter routes by
     * @return the routes making up this solution for the given depot
     */
    List<Route> getRoutes(Depot depot) {
        return this.Routes.getOrDefault(depot, new LinkedList<>());
    }

    /**
     * @return the routes making up this solution, grouped by serving depot
     */
    Map<Depot, List<Route>> getRoutesByDepot() {
        return this.Routes;
    }

    /**
     * @return the number of routes (vehicles) used
     */
    int getRoutesCount() {
        int count = 0;
        for (List<Route> depotRoutes : this.Routes.values())
            count += depotRoutes.size();
        return count;
    }

    /**
     * @return the number of depots opened by this solution
     */
    int getDepotsCount() {
        return this.Routes.size();
    }

    /**
     * @return the total travelled distance of the solution
     */
    public double getTotalDistance() {
        return this.TotalDistance;
    }

    /**
     * Flattens the routes back into a single giant-tour sequence by
     * concatenating their stops.
     *
     * @return the concatenated stop sequence
     */
    int[] getNewSequence() {
        int[] sequence = new int[this.Stops.cardinality()];
        int index = 0;
        for (List<Route> depotRoutes : this.Routes.values())
            for (Route route : depotRoutes)
                for (int stop : route.getSequence())
                    sequence[index++] = stop;
        return sequence;
    }

    /**
     * Renders one line per route, grouped by the depot it is assigned to and
     * ordered by cost within each depot, then the opened-depot count and the
     * total cost.
     *
     * @return the formatted solution
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        List<Route> sortedRoutes = this.getRoutes();
        sortedRoutes.sort(Comparator.comparingInt((Route r) -> r.getDepot().index())
                                    .thenComparing(Comparator.naturalOrder()));
        for (Route r : sortedRoutes) {
            sb.append("Depot ").append(r.getDepot().index()).append(" serves ");
            sb.append(r.getLength()).append(" stops : ");
            sb.append(r.toString()).append(" = ").append(String.format(Locale.US, "%.2f", r.getTraveledDistance()));
            sb.append("\n");
        }
        sb.append("Opened depots = ").append(this.getDepotsCount());
        sb.append(", total cost = ").append(String.format(Locale.US, "%.2f", this.TotalDistance));
        return sb.toString();
    }

    /**
     * Renders the solution as one {@code Route #k (depot d): ...} line per
     * vehicle, stops in 1-based numbering.
     *
     * @return the formatted route listing
     */
    String export() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        List<Route> sortedRoutes = this.getRoutes();
        Collections.sort(sortedRoutes);
        for (Route r : sortedRoutes) {
            sb.append("Route #").append(++i).append(" (depot ").append(r.getDepot().index()).append("): ");
            sb.append(r.export());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Orders solutions by ascending total travelled distance.
     *
     * @param sol the solution to compare against
     * @return a negative value, zero or a positive value as this solution is
     *         cheaper than, equal to, or costlier than {@code sol}
     */
    @Override
    public int compareTo(Solution sol) {
        return Double.compare(this.TotalDistance * 100d, sol.TotalDistance * 100d);
    }

    /**
     * Releases the solution by closing all of its routes and clearing the route
     * and stop sets. Because routes may be shared with other solutions, do not
     * close a solution whose routes are still in use elsewhere.
     */
    @Override
    public void close() {
        for (Route route : this.getRoutes())
            route.close();
        this.Routes.clear();
        this.DepotLoads.clear();
        this.Stops.clear();
    }
}