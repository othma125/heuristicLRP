// Author: Othmane

package Algorithm.Solution.LSM;


import Algorithm.Data.InputData;
import Algorithm.Solution.Route;
import Algorithm.Solution.Solution;


/**
 * Base class for local search moves. A move is defined by two positions
 * ({@code i}, {@code j}) over either one route (intra-route) or two routes
 * (inter-route), and knows how to evaluate its cost change, test its
 * capacity feasibility, and apply itself. Subclasses implement the specific
 * neighbourhoods: {@link _2Opt}, {@link Swap}, {@link LeftShift},
 * {@link RightShift}.
 *
 * @author Othmane EL YAAKOUBI
 */
public abstract class LocalSearchMove {

    final String name;
    final boolean oneSequence;
    final int i, j;
    final int border;
    Route firstRoute, secondRoute;
    double gain = 0d;

    /**
     * Computes the change in total distance the move would produce and stores
     * it in {@code gain} (negative means improving).
     *
     * @param data the problem instance providing distances
     */
    public abstract void setGain(InputData data);

    /**
     * Applies the move, mutating the route(s) in place or rebuilding them.
     *
     * @param data the problem instance providing distances
     */
    public abstract void perform(InputData data);

    /**
     * @param data     the problem instance providing demands and capacity
     * @param solution the solution the routes belong to, used to price the
     *                 depot room a move across two depots needs, or
     *                 {@code null} when no depot ships anything yet
     * @return {@code true} if applying the move keeps both routes within
     *         vehicle capacity and both depots within theirs
     */
    public abstract boolean isFeasible(InputData data, Solution solution);

    /**
     * Depot side of the feasibility test. Stops handed to a route of another
     * depot take their demand with them, so the receiving depot has to have
     * room for what its route ends up shipping. Within one depot the demand
     * only moves between its own routes, so nothing has to be checked.
     *
     * @param solution the solution the routes belong to, or {@code null}
     * @param route    the route whose depot receives the demand
     * @param demand   the demand that route ships once the move is applied
     * @return {@code true} if the depot can ship it
     */
    boolean hasRoom(Solution solution, Route route, int demand) {
        if (this.firstRoute.getDepot().equals(this.secondRoute.getDepot()))
            return true;
        return demand <= (solution == null ? route.getDepot().capacity() : solution.getLeftOver(route));
    }

    /**
     * Whether a route may be emptied without losing the depot opening cost it
     * carries. {@link #rebuild} hands that charge over to the other route when
     * both serve the same depot, but across two depots there is nobody to hand
     * it to: the charge may only go away with the depot itself.
     *
     * @param solution the solution the route belongs to, or {@code null}
     * @param route    the route the move would empty
     * @return {@code true} if emptying it leaves every opened depot paid for
     */
    boolean keepsDepotPaid(Solution solution, Route route) {
        if (this.firstRoute.getDepot().equals(this.secondRoute.getDepot()) || !route.paysDepotOpening())
            return true;
        return solution == null || solution.closesDepot(route);
    }

    /**
     * @param name   the move's name, used in {@code toString}
     * @param i      the first position
     * @param j      the second position
     * @param routes one route for an intra-route move, or two for an
     *               inter-route move
     * @throws IllegalArgumentException if a single-route move has
     *         {@code i >= j}, or if more than two routes are given
     */
    LocalSearchMove(String name, int i, int j, Route ... routes) {
        this.name = name;
        if(routes.length == 1 && i >= j)
            throw new IllegalArgumentException("i should be smaller than j in LSM");
        if(routes.length > 2)
            throw new IllegalArgumentException("routes number should be equals to 1 or 2 in LSM");
        this.gain = 0d;
        this.i = i;
        this.j = j;
        this.oneSequence = routes.length == 1;
        this.firstRoute = routes[0];
        this.secondRoute = this.oneSequence ? this.firstRoute : routes[1];
        this.border = this.oneSequence ? this.firstRoute.getLength() : this.secondRoute.getLength();
    }

    /**
     * Rebuilds both routes after an inter-route move. A depot keeps exactly one
     * route carrying its opening cost, so the charge is inherited from the route
     * each sequence replaces, and handed over when its carrier disappears and
     * the surviving route serves the same depot.
     *
     * @param data the problem instance providing distances and demands
     * @param seq1 the new sequence of the first route, empty if it disappears
     * @param seq2 the new sequence of the second route, empty if it disappears
     */
    void rebuild(InputData data, int[] seq1, int[] seq2) {
        Route first = this.firstRoute, second = this.secondRoute;
        boolean paysFirst = first.paysDepotOpening();
        boolean paysSecond = second.paysDepotOpening();
        boolean sameDepot = first.getDepot().equals(second.getDepot());
        if (sameDepot && seq1.length == 0 && paysFirst) {
            paysFirst = false;
            paysSecond = true;
        }
        else if (sameDepot && seq2.length == 0 && paysSecond) {
            paysSecond = false;
            paysFirst = true;
        }
        this.firstRoute = seq1.length > 0 ? new Route(data, first.getDepot(), seq1, paysFirst) : null;
        this.secondRoute = seq2.length > 0 ? new Route(data, second.getDepot(), seq2, paysSecond) : null;
    }

    /**
     * @return the cost change of the move (negative means improving)
     */
    public double getGain() {
        return this.gain;
    }

    /**
     * @return the first route after the move (may be {@code null} if the move
     *         emptied it)
     */
    public Route getFirstRoute() {
        return this.firstRoute;
    }

    /**
     * @return the second route after the move (may be {@code null} if the move
     *         emptied it)
     */
    public Route getSecondRoute() {
        return this.secondRoute;
    }
}