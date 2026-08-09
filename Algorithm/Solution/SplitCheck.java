// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.Depot;
import Algorithm.Data.InputData;
import java.util.List;

/**
 * Self-check for the multi-depot split: splits a random giant tour and verifies
 * that every customer is served exactly once, that no route exceeds the vehicle
 * capacity, that each route's cached cost matches a recomputation from its own
 * depot, and that the solution total is the sum of its routes.
 *
 * <p>ponytail: a runnable check instead of a test framework, run with
 * {@code java -cp out Algorithm.Solution.SplitCheck} from the project root.
 *
 * @author Othmane EL YAAKOUBI
 */
public class SplitCheck {

    /**
     * @param args optional instance path, then an optional number of tours to split
     * @throws Exception if the instance cannot be read
     */
    public static void main(String[] args) throws Exception {
        String file = args.length > 0 ? args[0] : "Algorithm/LRPLib/Instances_Prodhon_LRP/coord20-5-1.dat";
        int repetitions = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        InputData data = new InputData(file);
        for (int run = 0; run < repetitions; run++)
            check(data);
        System.exit(0);
    }

    /**
     * Splits one random giant tour and verifies the solution it produces.
     *
     * @param data the instance to split
     */
    private static void check(InputData data) {
        GiantTour gt;
        do {
            gt = new GiantTour(data);
        } while (!gt.isFeasible());

        Solution best = gt.AuxiliaryGraph.getLastNode().getBestSolution();
        List<Route> routes = best.getRoutes();
        int[] served = new int[data.getCustomerNumber()];
        double total = 0d;
        for (Route route : routes) {
            for (int stop : route.getSequence())
                served[stop]++;
            if (route.getSumDemand() > data.getCapacity())
                throw new AssertionError("route over capacity: " + route.getSumDemand());
            double recomputed = data.getDepotToStopDistance(route.getDepot(), route.getFirst());
            for (int k = 0; k < route.getLength() - 1; k++)
                recomputed += data.getTwoStopsDistance(route.getStop(k), route.getStop(k + 1));
            recomputed += data.getStopToDepotDistance(route.getLast(), route.getDepot());
            recomputed += data.getRouteCost();
            if (route.paysDepotOpening())
                recomputed += route.getDepot().openingCost();
            if (Math.abs(recomputed - route.getTraveledDistance()) > 1e-6)
                throw new AssertionError("route cost " + route.getTraveledDistance() + " != " + recomputed
                        + " from depot " + route.getDepot().index() + " : " + route);
            total += route.getTraveledDistance();
        }
        for (int stop = 0; stop < served.length; stop++)
            if (served[stop] != 1)
                throw new AssertionError("customer " + stop + " served " + served[stop] + " times");
        for (Depot depot : data.getDepots()) {
            int load = 0, payers = 0;
            for (Route route : best.getRoutes(depot)) {
                load += route.getSumDemand();
                if (route.paysDepotOpening())
                    payers++;
            }
            if (load > depot.capacity())
                throw new AssertionError("depot " + depot.index() + " ships " + load
                        + " over a capacity of " + depot.capacity());
            // An open depot is paid for exactly once, a closed one not at all.
            int expected = best.getRoutes(depot).isEmpty() ? 0 : 1;
            if (payers != expected)
                throw new AssertionError("depot " + depot.index() + " charged " + payers
                        + " times instead of " + expected);
        }
        if (Math.abs(total - best.getTotalDistance()) > 1e-6)
            throw new AssertionError("total " + best.getTotalDistance() + " != sum of routes " + total);
        System.out.println("ok, " + routes.size() + " routes over " + best.getDepotsCount()
                + " depots, distance " + String.format(java.util.Locale.US, "%.2f", best.getTotalDistance()));
    }
}
