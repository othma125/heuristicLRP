// Author: Othmane

package Algorithm.Solution.LSM;

import Algorithm.Data.Depot;
import Algorithm.Data.InputData;
import Algorithm.Solution.Route;

/**
 * Self-check for the inter-route moves once the two routes are served from
 * different depots. For every move and every legal pair of positions it
 * compares the announced gain with the distance actually observed after the
 * move, both routes recomputed from scratch, so a leg attached to the wrong
 * depot shows up as a mismatch.
 *
 * <p>ponytail: a runnable check instead of a test framework, run with
 * {@code java -cp out Algorithm.Solution.LSM.GainCheck} from the project root.
 *
 * @author Othmane EL YAAKOUBI
 */
public class GainCheck {

    private static int[] FIRST_STOPS;
    private static int[] SECOND_STOPS;
    private static InputData Data;
    private static Depot FirstDepot, SecondDepot;
    private static int Checked = 0;

    /**
     * @param args the command line arguments (unused)
     * @throws Exception if the instance cannot be read
     */
    public static void main(String[] args) throws Exception {
        Data = new InputData("Algorithm/LRPLib/Instances_Prodhon_LRP/coord20-5-1.dat");
        FirstDepot = Data.getDepots()[0];
        SecondDepot = Data.getDepots()[3];

        sweep(new int[]{0, 1, 2, 3, 4}, new int[]{5, 6, 7, 8});
        sweep(new int[]{0, 1}, new int[]{5, 6});        // a move can empty the first route
        sweep(new int[]{0, 1, 2}, new int[]{5});        // a move can empty the second route
        SecondDepot = FirstDepot;                       // the single-depot case still holds
        sweep(new int[]{0, 1, 2, 3, 4}, new int[]{5, 6, 7, 8});
        System.out.println("ok, " + Checked + " moves priced correctly");
    }

    /**
     * Runs every legal move over the two given stop sequences.
     *
     * @param first  the stops of the route served by the first depot
     * @param second the stops of the route served by the second depot
     */
    private static void sweep(int[] first, int[] second) {
        FIRST_STOPS = first;
        SECOND_STOPS = second;
        int length1 = FIRST_STOPS.length;
        int length2 = SECOND_STOPS.length;
        int max1 = (int) Math.sqrt(length1);
        int max2 = (int) Math.sqrt(length2);
        for (int i = 0; i < length1; i++)
            for (int j = 0; j < length2; j++) {
                check("2Opt", new _2Opt(Data, i, j, first(), second()));
                check("Swap", new Swap(Data, i, j, first(), second()));
                for (int degree = j == i + 1 ? 1 : 0; degree <= max2 && j + degree < length2; degree++) {
                    check("RightShift", new RightShift(Data, true, degree, i, j, first(), second()));
                    if (degree > 0)
                        check("RightShift", new RightShift(Data, false, degree, i, j, first(), second()));
                }
                for (int degree = j == i + 1 ? 1 : 0; degree <= max1 && i - degree >= 0; degree++) {
                    check("LeftShift", new LeftShift(Data, true, degree, i, j, first(), second()));
                    if (degree > 0)
                        check("LeftShift", new LeftShift(Data, false, degree, i, j, first(), second()));
                }
            }
    }

    /** @return a fresh copy of the route served by the first depot, carrying its opening cost */
    private static Route first() {
        return new Route(Data, FirstDepot, FIRST_STOPS.clone(), true);
    }

    /**
     * @return a fresh copy of the route served by the second depot; it pays for
     *         its own depot only when that is a different depot from the first
     */
    private static Route second() {
        return new Route(Data, SecondDepot, SECOND_STOPS.clone(), !SecondDepot.equals(FirstDepot));
    }

    /**
     * Prices the move, applies it, and fails when the announced gain does not
     * match the observed change in distance.
     *
     * @param name the move's name, for the failure message
     * @param move the move to check
     */
    private static void check(String name, LocalSearchMove move) {
        double before = distance(move.getFirstRoute()) + distance(move.getSecondRoute());
        double charged_before = opening(move.getFirstRoute()) + opening(move.getSecondRoute());
        move.setGain(Data);
        double gain = move.getGain();
        move.Perform(Data);
        double after = distance(move.getFirstRoute()) + distance(move.getSecondRoute());
        double charged_after = opening(move.getFirstRoute()) + opening(move.getSecondRoute());
        if (Math.abs(after - before - gain) > 1e-6)
            throw new AssertionError(name + " " + move + " announced " + gain
                    + " but moved from " + before + " to " + after);
        // A move never opens a depot that was closed, so its charge can only stay or be saved.
        if (charged_after > charged_before + 1e-6)
            throw new AssertionError(name + " " + move + " raised the depot opening charge from "
                    + charged_before + " to " + charged_after);
        // A surviving route must keep its depot paid for.
        for (Route route : new Route[]{move.getFirstRoute(), move.getSecondRoute()})
            if (route != null && !route.paysDepotOpening()
                && !isPaidBy(move.getFirstRoute(), route) && !isPaidBy(move.getSecondRoute(), route))
                throw new AssertionError(name + " " + move + " left depot "
                        + route.getDepot().index() + " served but unpaid");
        Checked++;
    }

    /**
     * @param payer a route that may carry a depot charge
     * @param route the route whose depot needs paying for
     * @return {@code true} when {@code payer} carries the charge of that depot
     */
    private static boolean isPaidBy(Route payer, Route route) {
        return payer != null && payer.paysDepotOpening() && payer.getDepot().equals(route.getDepot());
    }

    /**
     * A move prices edges, not depot openings, so the check compares travelled
     * distance alone and audits the opening charge separately.
     *
     * @param route a route, or {@code null} when a move emptied it
     * @return the route's travelled distance, recomputed from its own depot
     */
    private static double distance(Route route) {
        if (route == null)
            return 0d;
        double distance = Data.getDepotToStopDistance(route.getDepot(), route.getFirst());
        for (int k = 0; k < route.getLength() - 1; k++)
            distance += Data.getTwoStopsDistance(route.getStop(k), route.getStop(k + 1));
        return distance + Data.getStopToDepotDistance(route.getLast(), route.getDepot());
    }

    /**
     * @param route a route, or {@code null} when a move emptied it
     * @return the depot opening cost this route carries, zero when it carries none
     */
    private static double opening(Route route) {
        return route != null && route.paysDepotOpening() ? route.getDepot().openingCost() : 0d;
    }
}
