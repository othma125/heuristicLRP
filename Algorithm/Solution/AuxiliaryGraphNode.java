// Author: Othmane

package Algorithm.Solution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import Algorithm.Data.InputData;

/**
 * A node of the {@link AuxiliaryGraph}, representing a position in the giant
 * tour. Each node holds the best partial solution derived from each distinct
 * predecessor label reaching it, keyed by that predecessor in
 * {@link #solutions}; the cheapest label of the last node is the split. A
 * label is kept when it beats the predecessor's current entry on either of two
 * minimised objectives, its cost or its leftover depot room, so the node ends
 * up holding a trade-off front rather than a single best; {@link #getParetoSet()}
 * extracts the non-dominated part of it. Label updates are guarded by a
 * {@link ReentrantLock} because the graph is built concurrently.
 *
 * @author Othmane EL YAAKOUBI
 */
public class AuxiliaryGraphNode implements AutoCloseable {

    private final Map<Solution, Solution> solutions = new HashMap<>();
    final ReentrantLock lock = new ReentrantLock();
    final int nodeIndex;

    /**
     * @param nodeIndex the position of this node in the giant tour
     */
    AuxiliaryGraphNode(int nodeIndex) {
        this.nodeIndex = nodeIndex;
    }

    /**
     * Relaxes this node with a solution formed by extending {@code oldSolution}
     * with one new route, keeping it when it improves either the node's label or
     * its leftover depot room.
     *
     * @param oldSolution the partial solution reaching the predecessor node,
     *                     or {@code null} for the source
     * @param newRoute    the route appended to reach this node
     * @return {@code true} if the node was already reachable when an improving
     *         label was accepted
     */
    boolean updateLabel(Solution oldSolution, Route newRoute) {
        if (newRoute == null)
            return false;
        boolean c = false;
        this.lock.lock();
        try {
            double label = (oldSolution == null ? 0d : oldSolution.getTotalDistance()) + newRoute.getTraveledDistance();
            int leftover = oldSolution == null ? newRoute.getDepot().capacity() - newRoute.getSumDemand()
                                                : oldSolution.getLeftoverLoadAfter(null, newRoute);
            if (label < this.getLabel(oldSolution) || leftover < this.getLeftoverLoad(oldSolution)) {
                c = this.isFeasible();
                Solution newSolution = new Solution(label, oldSolution == null ? 1 : oldSolution.getRoutesCount() + 1);
                if(oldSolution != null)
                    for(Route route : oldSolution.getRoutes())
                        newSolution.add(route);
                newSolution.add(newRoute);
                this.solutions.put(oldSolution, newSolution);
            }
        } finally {
            this.lock.unlock();
        }
        return c;
    }

    /**
     * Relaxes this node with a solution obtained by replacing {@code oldRoute}
     * with {@code newRoute} in {@code oldSolution}, keeping it if it improves
     * either the cost or the leftover depot room.
     *
     * @param oldSolution the partial solution to derive from
     * @param oldRoute    the route being replaced
     * @param newRoute    the replacement route
     * @return {@code true} if the node was already reachable when an improving
     *         label was accepted
     */
    boolean updateLabel(Solution oldSolution, Route oldRoute, Route newRoute) {
        if (newRoute == null)
            return false;
        boolean c = false;
        this.lock.lock();
        try {
            double label = oldSolution.getTotalDistance() - oldRoute.getTraveledDistance() + newRoute.getTraveledDistance();
            if (label < this.getLabel(oldSolution) || oldSolution.getLeftoverLoadAfter(oldRoute, newRoute) < this.getLeftoverLoad(oldSolution)) {
                c = this.isFeasible();
                Solution newSolution = new Solution(label, oldSolution.getRoutesCount());
                for (Route route : oldSolution.getRoutes())
                    newSolution.add(route == oldRoute ? newRoute : route);
                this.solutions.put(oldSolution, newSolution);
            }
        } finally {
            this.lock.unlock();
        }
        return c;
    }

    /**
     * Relaxes this node with a solution that replaces {@code oldRoute} with
     * two routes (the result of an inter-route move that splits into two),
     * keeping it if it improves either the cost or the leftover depot room.
     * Delegates to the single-route overload when one of the routes is
     * {@code null}.
     *
     * @param data         the problem instance
     * @param oldSolution the partial solution to derive from
     * @param oldRoute    the route being replaced
     * @param route1       the first replacement route (may be {@code null})
     * @param route2       the second replacement route (may be {@code null})
     */
    void updateLabel(InputData data, Solution oldSolution, Route oldRoute, Route route1, Route route2) {
        if (route1 == null) {
            this.updateLabel(oldSolution, oldRoute, route2);
            return;
        }
        else if (route2 == null) {
            this.updateLabel(oldSolution, oldRoute, route1);
            return;
        }
        this.lock.lock();
        try {
            double label = oldSolution.getTotalDistance() - oldRoute.getTraveledDistance() + route1.getTraveledDistance() + route2.getTraveledDistance();
            if (label < this.getLabel(oldSolution) || oldSolution.getLeftoverLoadAfter(oldRoute, route1, route2) < this.getLeftoverLoad(oldSolution)) {
                Solution newSolution = new Solution(label, oldSolution.getRoutesCount() + 1);
                for (Route route : oldSolution.getRoutes()) 
                    if (route != oldRoute) 
                        newSolution.add(route);
                newSolution.add(route1);
                newSolution.add(route2);
                this.solutions.put(oldSolution, newSolution);
            }
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * @return the smallest leftover among the solutions reaching this node, or
     *         {@link Integer#MAX_VALUE} if none does
     */
    int getLeftoverLoad() {
        return this.isFeasible() ? this.getBestLeftover().getLeftoverLoad() : Integer.MAX_VALUE;
    }

    /**
     * @param oldSolution the predecessor label whose map entry is looked up
     * @return the cost of the label derived from {@code oldSolution}, or
     *         {@link Double#POSITIVE_INFINITY} if the map holds no such key
     */
    double getLabel(Solution oldSolution) {
        Solution solution = this.solutions.get(oldSolution);
        return solution == null ? Double.POSITIVE_INFINITY : solution.getTotalDistance();
    }

    /**
     * @param oldSolution the predecessor label whose map entry is looked up
     * @return the leftover load of the label derived from {@code oldSolution},
     *         or {@link Integer#MAX_VALUE} if the map holds no such key
     */
    int getLeftoverLoad(Solution oldSolution) {
        Solution solution = this.solutions.get(oldSolution);
        return solution == null ? Integer.MAX_VALUE : solution.getLeftoverLoad();
    }

    /**
     * @return the solution reaching this node with the lowest leftover load
     */
    private Solution getBestLeftover() {
        return Collections.min(this.solutions.values(), Comparator.comparingInt(Solution::getLeftoverLoad));
    }

    /**
     * Extracts the non-dominated solutions of this node for the two minimised
     * objectives: total cost and leftover depot room. A solution is dominated
     * when another one is at least as good on both and strictly better on one.
     *
     * @return the Pareto-optimal solutions, cheapest first
     */
    List<Solution> getParetoSet() {
        LinkedList<Solution> pareto = new LinkedList<>();
        this.lock.lock();
        try {
            List<Solution> sorted = new ArrayList<>(this.solutions.values());
            sorted.sort(Comparator.comparingInt(Solution::getLeftoverLoad)
                                  .thenComparingDouble(Solution::getTotalDistance));
            double bestDistance = Double.POSITIVE_INFINITY;
            for (Solution solution : sorted)
                if (solution.getTotalDistance() < bestDistance) {
                    pareto.addFirst(solution);
                    bestDistance = solution.getTotalDistance();
                }
        } finally {
            this.lock.unlock();
        }
        return pareto;
    }

    /**
     * @return the current best (lowest-cost) solution reaching this node
     */
    Solution getBestSolution() {
        return Collections.min(this.solutions.values(), Comparator.comparingDouble(Solution::getTotalDistance));
    }

    /**
     * @return all candidate solutions currently held at this node, one per
     *         predecessor label
     */
    Collection<Solution> getSolutions() {
        return this.solutions.values();
    }

    /**
     * @return {@code true} if at least one solution reaches this node
     */
    boolean isFeasible() {
         return !this.solutions.isEmpty();
    }

    @Override
    public String toString() {
        return this.isFeasible() ? this.getBestSolution().toString() : "NULL";
    }

    /**
     * @return the CVRPLIB route listing of the best solution, or {@code "NULL"}
     *         if infeasible
     */
    String export() {
        return this.isFeasible() ? this.getBestSolution().export() : "NULL";
    }

    /**
     * @return the number of routes in the best solution, or 0 if infeasible
     */
    int getRoutesCount() {
        return this.isFeasible() ? this.getBestSolution().getRoutesCount() : 0;
    }

    /**
     * @return the cost of the best solution, or
     *         {@link Double#POSITIVE_INFINITY} if infeasible
     */
    double getLabel() {
        return this.isFeasible() ? this.getBestSolution().getTotalDistance() : Double.POSITIVE_INFINITY;
    }

    /**
     * Returns the flattened giant-tour sequence of the best solution. Inter-route
     * local search is applied to the sink's Pareto set in the
     * {@link AuxiliaryGraph} constructor, so the returned sequence already
     * reflects those improvements.
     *
     * @param data the problem instance
     * @return the flattened sequence of the best solution, or {@code null} if
     *         infeasible
     */
    int[] getNewSequence(InputData data) {
        if (this.isFeasible()) {
            int[] seq = null;
            this.lock.lock();
            try {
                seq = this.getBestSolution().getNewSequence();
            } finally {
                this.lock.unlock();
            }
            return seq;
        }
        return null;
    }

    /**
     * Releases the node by closing all of its solutions and clearing the list.
     * Guarded by the node {@link #lock} since the graph is built concurrently.
     */
    @Override
    public void close() {
        this.lock.lock();
        try {
            for (Solution solution : this.solutions.values())
                solution.close();
            this.solutions.clear();
        } finally {
            this.lock.unlock();
        }
    }
}