// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.InputData;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import java.util.LinkedList;
import java.util.Comparator;

/**
 * A node of the {@link AuxiliaryGraph}, representing a position in the giant
 * tour. Each node holds the partial solutions (labels) reaching it; the
 * cheapest label of the last node is the split. A label is kept when it beats
 * the node on either of two minimised objectives, its cost or its leftover
 * depot room, so the node ends up holding a trade-off front rather than a
 * single best; {@link #getParetoSet()} extracts the non-dominated part of it.
 * Label updates are guarded by a {@link ReentrantLock} because the graph is
 * built concurrently.
 *
 * @author Othmane EL YAAKOUBI
 */
public class AuxiliaryGraphNode implements AutoCloseable {

    private final List<Solution> Solutions = new LinkedList<>();
    // Cached because every relaxation tests against it, and recomputing would walk
    // every solution held here.
    private int BestLeftOver = Integer.MAX_VALUE;
    final ReentrantLock Lock = new ReentrantLock();
    final int NodeIndex;

    /**
     * @param NodeIndex the position of this node in the giant tour
     */
    AuxiliaryGraphNode(int NodeIndex) {
        this.NodeIndex = NodeIndex;
    }

    /**
     * Relaxes this node with a solution formed by extending {@code old_solution}
     * with one new route, keeping it when it improves either the node's label or
     * its leftover depot room.
     *
     * @param old_solution the partial solution reaching the predecessor node,
     *                     or {@code null} for the source
     * @param new_route    the route appended to reach this node
     * @return {@code true} if the node was already reachable when an improving
     *         label was accepted
     */
    boolean UpdateLabel(Solution old_solution, Route new_route) {
        if (new_route == null)
            return false;
        boolean c = false;
        this.Lock.lock();
        try {
            double label = (old_solution == null ? 0d : old_solution.getTotalDistance()) + new_route.getTraveledDistance();
            int leftover = old_solution == null ? new_route.getDepot().capacity() - new_route.getSumDemand()
                                                : old_solution.getLeftoverLoadAfter(null, new_route);
            if (label < this.getLabel() || leftover < this.getLeftoverLoad()) {
                c = this.getLabel() < Double.POSITIVE_INFINITY;
                Solution newSolution = new Solution(label, old_solution == null ? 1 : old_solution.getRoutesCount() + 1);
                if(old_solution != null)
                    for(Route route : old_solution.getRoutes())
                        newSolution.add(route);
                newSolution.add(new_route);
                this.keep(newSolution, label < this.getLabel());
            }
        } finally {
            this.Lock.unlock();
        }
        return c;
    }

    /**
     * Relaxes this node with a solution obtained by replacing {@code old_route}
     * with {@code new_route} in {@code old_solution}, keeping it if it improves
     * either the cost or the leftover depot room.
     *
     * @param old_solution the partial solution to derive from
     * @param old_route    the route being replaced
     * @param new_route    the replacement route
     * @return {@code true} if the node was already reachable when an improving
     *         label was accepted
     */
    boolean UpdateLabel(Solution old_solution, Route old_route, Route new_route) {
        if (new_route == null)
            return false;
        boolean c = false;
        this.Lock.lock();
        try {
            double label = old_solution.getTotalDistance() - old_route.getTraveledDistance() + new_route.getTraveledDistance();
            if (label < this.getLabel() || old_solution.getLeftoverLoadAfter(old_route, new_route) < this.getLeftoverLoad()) {
                c = this.getLabel() < Double.POSITIVE_INFINITY;
                Solution newSolution = new Solution(label, old_solution.getRoutesCount());
                for (Route route : old_solution.getRoutes())
                    newSolution.add(route == old_route ? new_route : route);
                this.keep(newSolution, label < this.getLabel());
            }
        } finally {
            this.Lock.unlock();
        }
        return c;
    }

    /**
     * Relaxes this node with a solution that replaces {@code old_route} with
     * two routes (the result of an inter-route move that splits into two),
     * keeping it if it improves either the cost or the leftover depot room.
     * Delegates to the single-route overload when one of the routes is
     * {@code null}.
     *
     * @param data         the problem instance
     * @param old_solution the partial solution to derive from
     * @param old_route    the route being replaced
     * @param route1       the first replacement route (may be {@code null})
     * @param route2       the second replacement route (may be {@code null})
     */
    void UpdateLabel(InputData data, Solution old_solution, Route old_route, Route route1, Route route2) {
        if (route1 == null) {
            this.UpdateLabel(old_solution, old_route, route2);
            return;
        }
        else if (route2 == null) {
            this.UpdateLabel(old_solution, old_route, route1);
            return;
        }
        this.Lock.lock();
        try {
            double label = old_solution.getTotalDistance() - old_route.getTraveledDistance() + route1.getTraveledDistance() + route2.getTraveledDistance();
            if (label < this.getLabel() || old_solution.getLeftoverLoadAfter(old_route, route1, route2) < this.getLeftoverLoad()) {
                Solution newSolution = new Solution(label, old_solution.getRoutesCount() + 1);
                for (Route route : old_solution.getRoutes()) 
                    if (route != old_route) 
                        newSolution.add(route);
                newSolution.add(route1);
                newSolution.add(route2);
                this.keep(newSolution, label < this.getLabel());
            }
        } finally {
            this.Lock.unlock();
        }
    }

    /**
     * Files an accepted solution and keeps the cached leftover in step. Improving
     * labels go to the front so the cheapest solution is found first.
     *
     * @param solution  the accepted solution
     * @param improving whether it beats the node's current label
     */
    private void keep(Solution solution, boolean improving) {
        if (improving)
            this.Solutions.addFirst(solution);
        else
            this.Solutions.add(solution);
        this.BestLeftOver = Math.min(this.BestLeftOver, solution.getLeftoverLoad());
    }

    /**
     * @return the smallest leftover among the solutions reaching this node, or
     *         {@link Integer#MAX_VALUE} if none does
     */
    int getLeftoverLoad() {
        return this.BestLeftOver;
    }

    /**
     * Extracts the non-dominated solutions of this node for the two minimised
     * objectives: total cost and leftover depot room. A solution is dominated
     * when another one is at least as good on both and strictly better on one.
     *
     * @return the Pareto-optimal solutions, cheapest first
     */
    List<Solution> getParetoSet() {
        List<Solution> pareto = new LinkedList<>();
        this.Lock.lock();
        try {
            this.Solutions.sort(Comparator.comparingInt(Solution::getLeftoverLoad)
                                          .thenComparingDouble(Solution::getTotalDistance));
            double best_distance = Double.POSITIVE_INFINITY;
            for (Solution solution : this.Solutions)
                if (solution.getTotalDistance() < best_distance) {
                    pareto.addFirst(solution);
                    best_distance = solution.getTotalDistance();
                }
        } finally {
            this.Lock.unlock();
        }
        return pareto;
    }

    /**
     * @return the current best (lowest-cost) solution reaching this node
     */
    Solution getBestSolution() {
        Solution best = null;
        for (Solution solution : this.getSolutions()) 
            if (best == null || solution.getTotalDistance() < best.getTotalDistance()) 
                best = solution;
        return best;
    }

    /**
     * @return all candidate solutions currently held at this node
     */
    List<Solution> getSolutions() {
        return this.Solutions;
    }

    /**
     * @return {@code true} if at least one solution reaches this node
     */
    boolean isFeasible() {
         return !this.Solutions.isEmpty();
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
            this.Lock.lock();
            try {
                seq = this.getBestSolution().getNewSequence();
            } finally {
                this.Lock.unlock();
            }
            return seq;
        }
        return null;
    }

    /**
     * Releases the node by closing all of its solutions and clearing the list.
     * Guarded by the node {@link #Lock} since the graph is built concurrently.
     */
    @Override
    public void close() {
        this.Lock.lock();
        try {
            for (Solution solution : this.Solutions)
                solution.close();
            this.Solutions.clear();
            this.BestLeftOver = Integer.MAX_VALUE;
        } finally {
            this.Lock.unlock();
        }
    }
}