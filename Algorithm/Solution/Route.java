// Author: Othmane

package Algorithm.Solution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import Algorithm.Data.Depot;
import Algorithm.Data.InputData;
import Algorithm.Solution.LSM.LeftShift;
import Algorithm.Solution.LSM.LocalSearchMove;
import Algorithm.Solution.LSM.RightShift;
import Algorithm.Solution.LSM.Swap;
import Algorithm.Solution.LSM._2Opt;

/**
 * A single vehicle route: an ordered sequence of customer stops, served from
 * the {@link Depot} the route starts and ends at, together with its total
 * demand and travelled distance. Provides the intra-route and inter-route
 * local search moves (2-opt, swap, left/right shift) used to improve
 * solutions, along with the elementary array operations that apply an
 * accepted move.
 *
 * @author Othmane EL YAAKOUBI
 */
public final class Route implements Comparable<Route>, AutoCloseable {

    private final Depot depot;
    private int[] sequence;
    private int sumDemand;
    private double cost;
    // A depot is paid for once however many routes leave it, so the charge rides on the first
    // route assigned to it and every later route of the same depot travels free of it.
    private boolean payDepotOpening;
    private boolean isClosed = false;

    @Override
    public int hashCode() {
        int hash = this.sequence.length;
        hash = 31 * hash + this.sumDemand;
        hash = 31 * hash + this.depot.index();
        int sum = 0;
        for (int value : this.sequence)
            sum += value;
        return 31 * hash + sum;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (this.getClass() != obj.getClass())
            return false;
        final Route other = (Route) obj;
        if (this.sequence.length != other.sequence.length || this.sumDemand != other.sumDemand
            || !this.depot.equals(other.depot))
            return false;
        return Arrays.equals(this.sequence, other.sequence);
    }

    /**
     * Builds a route with precomputed cost, avoiding a distance recomputation.
     *
     * @param depot      the depot the route starts and ends at
     * @param seq        the ordered stop sequence
     * @param sumDemand the total demand of the sequence
     * @param dist       the total travelled distance of the sequence
     */
    public Route(Depot depot, int[] seq, int sumDemand, double dist) {
        this.depot = depot;
        this.sequence = seq;
        this.sumDemand = sumDemand;
        this.cost = dist;
    }

    /**
     * Builds a route serving a solution and computes its cost and demand. The
     * route pays for opening its depot when the solution has no route there yet.
     *
     * @param data     the problem instance providing distances and demands
     * @param solution the solution the route joins, or {@code null} for a route
     *                 that stands alone and therefore opens its depot itself
     * @param depot    the depot the route starts and ends at
     * @param seq      the ordered stop sequence
     */
    public Route(InputData data, Solution solution, Depot depot, int[] seq) {
        this.depot = depot;
        this.sequence = seq;
        this.setCost(data, solution);
    }

    /**
     * Builds a route with the depot opening charge decided by the caller, used
     * when the route replaces another one and inherits its share of the charge.
     *
     * @param data              the problem instance providing distances and demands
     * @param depot             the depot the route starts and ends at
     * @param seq               the ordered stop sequence
     * @param paysDepotOpening  whether this route carries the depot opening cost
     */
    public Route(InputData data, Depot depot, int[] seq, boolean paysDepotOpening) {
        this.depot = depot;
        this.sequence = seq;
        this.setCost(data, paysDepotOpening);
    }

    /**
     * Computes and stores the cost and total demand of the current sequence,
     * charging the depot opening cost when this is the first route the solution
     * assigns to that depot.
     *
     * @param data     the problem instance providing distances and demands
     * @param solution the solution the route belongs to, or {@code null}
     */
    public void setCost(InputData data, Solution solution) {
        this.setCost(data, solution == null || solution.getRoutes(this.depot).isEmpty());
    }

    /**
     * Computes and stores the cost and total demand of the current sequence:
     * both legs measured from the route's depot, plus the depot opening cost
     * when this route is the one carrying it.
     *
     * @param data             the problem instance providing distances and demands
     * @param paysDepotOpening whether this route carries the depot opening cost
     */
    public void setCost(InputData data, boolean paysDepotOpening) {
        this.cost = data.getDepotToStopDistance(this.depot, this.sequence[0]);
        this.sumDemand = 0;
        int i = 0;
        while (i < this.sequence.length - 1) {
            this.sumDemand += data.getDemand(this.sequence[i]);
            this.cost += data.getTwoStopsDistance(this.sequence[i], this.sequence[++i]);
        }
        this.cost += data.getStopToDepotDistance(this.sequence[i], this.depot);
        this.sumDemand += data.getDemand(this.sequence[i]);
        // Every route needs a vehicle, and the first route of a depot pays for opening it.
        this.cost += data.getRouteCost();
        this.payDepotOpening = paysDepotOpening;
        if (this.payDepotOpening)
            this.cost += this.depot.openingCost();
    }

    /**
     * @return {@code true} when this route carries its depot's opening cost
     */
    public boolean paysDepotOpening() {
        return this.payDepotOpening;
    }
    
    /**
     * Diversification pass that scans all index pairs for the single best
     * intra-route move (swap, left shift or right shift, with varying degree)
     * and applies it if it is improving. Used to escape local optima when the
     * regular local search stalls.
     *
     * <p>Gives up as soon as a stop is requested, reporting no move.
     *
     * @param data the problem instance providing distances and demands
     * @return {@code true} if an improving move was found and applied
     */
    public boolean stagnationBreaker(InputData data) {
	    int max = (int) Math.sqrt(this.sequence.length);
        for (int i = 0; i < this.sequence.length - 1 && !data.isStopRequested(); i++) {
            LocalSearchMove bestLsm = null;
            for (int j = i + 1; j < this.sequence.length; j++) {   
                if (j > i + 1) {
                    LocalSearchMove lsm = new Swap(data, i, j, this);
                    lsm.setGain(data);
                    if (bestLsm == null || lsm.getGain() < bestLsm.getGain())
                        bestLsm = lsm;
                }
                for (int degree = j == i + 1 ? 1 : 0; degree <= max && j + degree < this.sequence.length; degree++) {
                    LocalSearchMove lsm1 = new RightShift(data, true, degree, i, j, this);
                    lsm1.setGain(data);
                    if (bestLsm == null || lsm1.getGain() < bestLsm.getGain())
                        bestLsm = lsm1;
                    if (degree == 0)
                        continue;
                    LocalSearchMove lsm2 = new RightShift(data, false, degree, i, j, this);
                    lsm2.setGain(data);
                    if (bestLsm == null || lsm2.getGain() < bestLsm.getGain())
                        bestLsm = lsm2;
                }
                for (int degree = j == i + 1 ? 1 : 0; degree <= max && i - degree >= 0; degree++) {
                    LocalSearchMove lsm1 = new LeftShift(data, true, degree, i, j, this);
                    lsm1.setGain(data);
                    if (bestLsm == null || lsm1.getGain() < bestLsm.getGain())
                        bestLsm = lsm1;
                    if (degree == 0)
                        continue;
                    LocalSearchMove lsm2 = new LeftShift(data, false, degree, i, j, this);
                    lsm2.setGain(data);
                    if (bestLsm == null || lsm2.getGain() < bestLsm.getGain())
                        bestLsm = lsm2;
                }
            }          
            if (bestLsm != null && bestLsm.getGain() < 0d) {
                bestLsm.perform(data);
                return true;
            }
        }
        return false;
    }

    /**
     * Runs intra-route local search with a default restart probability derived
     * from the route length. No-op for routes of two stops or fewer.
     *
     * @param data the problem instance providing distances and demands
     */
    public void intraRoutesLocalSearch(InputData data) {
        if (this.sequence.length <= 2)
            return;
        this.intraRoutesLocalSearch(data, Math.sqrt(this.sequence.length) / this.sequence.length);
    }

    /**
     * Applies improving 2-opt moves within the route, capped at
     * {@code sqrt(length)} improvements per pass, then probabilistically either
     * repeats the pass or invokes {@link #stagnationBreaker(InputData)}. Returns
     * immediately once a stop has been requested.
     *
     * @param data        the problem instance providing distances and demands
     * @param probability controls how likely the search is to stop rather than
     *                    recurse for another pass
     */
    public void intraRoutesLocalSearch(InputData data, double probability) {
        // Every local-search pass in the split graph funnels through here, so this is the
        // one place a stop request has to be honoured: without it a Stop click waits
        // minutes for the recursion to unwind on a large instance.
        if (data.isStopRequested())
            return;
        int max = (int) Math.sqrt(this.sequence.length);
        int improvementCounter = 0;
        for (int i = 0; improvementCounter < max && i < this.sequence.length - 1 && !data.isStopRequested(); i++)
            for (int j = i + 1; improvementCounter < max && j < this.sequence.length ; j++) {
                LocalSearchMove lsm = new _2Opt(data, i , j, this);
                lsm.setGain(data);
                if (lsm.getGain() < 0d) {
                    lsm.perform(data);
                    improvementCounter++;
                }
            }
        boolean again = ThreadLocalRandom.current().nextDouble() > probability;
        if ((again && improvementCounter > 0) || (!again && improvementCounter < max && this.stagnationBreaker(data))) 
            this.intraRoutesLocalSearch(data);
    }
    
    /**
     * Searches for the first improving, feasible inter-route move between this
     * route and {@code other}, trying 2-opt, swap and left/right shift moves in
     * turn. Swaps and shifts also run between routes of two different depots,
     * where the stops they move change depot and the solution says whether the
     * receiving one has room; 2-opt stays within a depot, so its reconnections
     * are not even enumerated across two.
     *
     * @param data     the problem instance providing distances and capacity
     * @param other    the other route to exchange stops with
     * @param solution the solution the routes belong to, or {@code null} when
     *                 no depot ships anything yet
     * @return an improving feasible move, or {@code null} if none exists or a
     *         stop was requested mid-scan
     */
    public LocalSearchMove getLSM(InputData data, Route other, Solution solution) {
        LocalSearchMove lsm;
        if (this.depot.equals(other.depot)) {
            for (int i = 0; i < this.sequence.length && !data.isStopRequested(); i++)
                for (int j = 0; j < other.sequence.length ; j++) {
                    lsm = new _2Opt(data, i , j, this, other);
                    lsm.setGain(data);
                    if (lsm.getGain() < 0d && lsm.isFeasible(data, solution))
                        return lsm;
                }
            for (int i = 0; i < other.sequence.length && !data.isStopRequested(); i++)
                for (int j = 0; j < this.sequence.length ; j++) {
                    lsm = new _2Opt(data, i , j, other, this);
                    lsm.setGain(data);
                    if (lsm.getGain() < 0d && lsm.isFeasible(data, solution))
                        return lsm;
                }
        }
        for (int i = 0; i < this.sequence.length && !data.isStopRequested(); i++)
            for (int j = 0; j < other.sequence.length ; j++) {
                lsm = new Swap(data, i, j, this, other);
                if (lsm.isFeasible(data, solution)) {
                    lsm.setGain(data);
                    if (lsm.getGain() < 0d)
                        return lsm;
                }
            }
        int max1 = (int) Math.sqrt(this.sequence.length);
        int max2 = (int) Math.sqrt(other.sequence.length);
        for (int i = 0; i < this.sequence.length && !data.isStopRequested(); i++)
            for (int j = 0; j < other.sequence.length ; j++) {
                for (int degree = j == i + 1 ? 1 : 0; degree <= max2 && j + degree < other.sequence.length; degree++) {
                    lsm = new RightShift(data, true, degree, i, j, this, other);
                    if (lsm.isFeasible(data, solution)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else if (other.getSumDemand() <= data.getCapacity())
                        break;
                    if (degree == 0)
                        continue;
                    lsm = new RightShift(data, false, degree, i, j, this, other);
                    if (lsm.isFeasible(data, solution)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else if (other.getSumDemand() <= data.getCapacity())
                        break;
                }
                for (int degree = j == i + 1 ? 1 : 0; degree <= max1 && i - degree >= 0; degree++) {
                    lsm = new LeftShift(data, true, degree, i, j, this, other);
                    if (lsm.isFeasible(data, solution)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else if (this.getSumDemand() <= data.getCapacity())
                        break;
                    if (degree == 0)
                        continue;
                    lsm = new LeftShift(data, false, degree, i, j, this, other);
                    if (lsm.isFeasible(data, solution)) {
                        lsm.setGain(data);
                        if (lsm.getGain() < 0d)
                            return lsm;
                    }
                    else if (this.getSumDemand() <= data.getCapacity())
                        break;
                }
            }
        return null;
    }
    
    /**
     * Orders routes by ascending travelled distance.
     *
     * @param route the route to compare against
     * @return a negative value, zero or a positive value as this route is
     *         shorter than, equal to, or longer than {@code route}
     */
    @Override
    public int compareTo(Route route) {
        return Double.compare(this.cost * 100d, route.getTraveledDistance() * 100d);
    }

    @Override
    public String toString() {
        int[] modifiedSequence = new int[this.sequence.length];
        for (int i = 0; i < this.sequence.length; i++)
            modifiedSequence[i] = this.sequence[i] + 1;
        return Arrays.toString(modifiedSequence);
    }

    /**
     * @return the depot the route starts and ends at
     */
    public Depot getDepot() {
        return this.depot;
    }

    /**
     * @return the stop sequence as a boxed list
     */
    public java.util.List<Integer> getSequenceAsList() {
        List<Integer> list = new ArrayList<>();
        for (int stop : this.sequence)
            list.add(stop);
        return list;
    }

    /**
     * @return the first stop of the route
     */
    public int getFirst() {
        return this.sequence[0];
    }

    /**
     * @return the last stop of the route
     */
    public int getLast() {
        return this.sequence[this.sequence.length - 1];
    }

    /**
     * @return the backing stop sequence (not a copy)
     */
    public int[] getSequence() {
        return this.sequence;
    }

    /**
     * @return the total demand served by the route
     */
    public int getSumDemand() {
        return this.sumDemand;
    }

    /**
     * @return the total travelled distance of the route
     */
    public double getTraveledDistance() {
        return this.cost;
    }

    /**
     * @param index position in the sequence
     * @return the stop at the given position
     */
    public int getStop(int index) {
        return this.sequence[index];
    }

    /**
     * @return the route's stops in CVRPLIB 1-based numbering, space-separated
     */
    String export() {
        StringBuilder sb = new StringBuilder();
        for (int stop : this.sequence)
            sb.append(stop + 1).append(" ");
        return sb.toString();
    }

    /**
     * @return the number of stops in the route
     */
    public int getLength() {
        return this.sequence.length;
    }

    /**
     * Adjusts the cached travelled distance by an accepted move's gain (a
     * negative gain shortens the route).
     *
     * @param gain the change in travelled distance
     */
    public void improve(double gain) {
        this.cost += gain;
    }

    /**
     * Swaps the stops at positions {@code i} and {@code j} in place.
     *
     * @param i first position
     * @param j second position
     */
    public void swap(int i, int j) {
        new Move(i, j).swap(this.sequence);
    }

    /**
     * Reverses the segment between positions {@code i} and {@code j} in place.
     *
     * @param i segment start
     * @param j segment end
     */
    public void _2Opt(int i, int j) {
        new Move(i, j)._2Opt(this.sequence);
    }

    /**
     * Applies a left shift of the given degree in place.
     *
     * @param i      target position
     * @param j      source position
     * @param degree number of extra stops moved together with the anchor
     * @param with2Opt  whether the shifted segment is reversed (2-opt variant)
     */
    public void leftShift(int i, int j, int degree, boolean with2Opt) {
        for (int k = 0; k <= degree; k++)
            new Move(i - k, with2Opt ? j : j - k).leftShift(this.sequence);
    }

    /**
     * Applies a right shift of the given degree in place.
     *
     * @param i      source position
     * @param j      target position
     * @param degree number of extra stops moved together with the anchor
     * @param with2Opt  whether the shifted segment is reversed (2-opt variant)
     */
    public void rightShift(int i, int j, int degree, boolean with2Opt) {
        for (int k = 0; k <= degree; k++)
            new Move(with2Opt ? i : i + k, j + k).rightShift(this.sequence);
    }

    /**
     * Releases the route by dropping its stop sequence. Idempotent: a second
     * call is a no-op. After closing, methods that read the sequence must not
     * be called.
     */
    @Override
    public void close() {
        if (this.isClosed)
            return;
        // No resources to release
        this.sequence = null;
        this.isClosed = true;
    }
}