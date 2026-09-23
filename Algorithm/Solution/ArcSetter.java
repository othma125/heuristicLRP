// Author: Othmane

package Algorithm.Solution;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

import Algorithm.Data.Depot;
import Algorithm.Solution.LSM.LocalSearchMove;

/**
 * A parallel task that, starting from one node and one partial solution,
 * grows candidate routes stop by stop along a giant tour and relaxes the
 * labels of the downstream nodes until capacity is exhausted. One task runs per
 * non-dominated label of the starting node, so the partial solution it carries
 * is what decides how much room each depot still has.
 *
 * @author Othmane EL YAAKOUBI
 */
public class ArcSetter extends RecursiveAction {

    private final AuxiliaryGraph graph;
    final AuxiliaryGraphNode startingNode;
    final int[] tour;
    final Solution solution;
    volatile int nodeProcessingWith;

    /**
     * @param graph   the auxiliary graph this setter belongs to
     * @param node    the node this setter starts from
     * @param solution the partial solution reaching {@code node}, or {@code null} for the source
     * @param tour    the giant tour snapshot whose ordering guides route growth
     */
    ArcSetter(AuxiliaryGraph graph, AuxiliaryGraphNode node, Solution solution, int[] tour) {
        this.graph = graph;
        this.startingNode = node;
        this.solution = solution;
        this.tour = tour;
        this.nodeProcessingWith = this.startingNode.nodeIndex;
    }

    /**
     * Walks forward from the starting node, accumulating stops into a candidate
     * route and, at each reachable node, relaxing its label with one candidate
     * route per depot that still has room for the segment (and with routes
     * merged into or split from the existing solution). Stops once capacity is
     * exceeded, then deregisters from the graph's {@link Phaser}.
     */
    @Override
    protected void compute() {
        try {
            int i = this.startingNode.nodeIndex;
            int j = this.startingNode.nodeIndex;
            int length = 0;
            int cumulativeDemand = 0;
            // The walk grows this buffer and copies it out per candidate route: an int[]
            // keeps the accumulation free of boxing and makes reading the stop just added
            // an array access instead of a linked list traversal.
            int[] sequence = new int[16];
            int size = 0;
            final Depot[] depots = this.graph.getData().getDepots();
            // The solution's routes do not change while this setter walks the tour, so the list
            // is taken once. It is shuffled because the scan below stops at the first improving
            // merge, and a fixed order would always try the same routes first.
            final List<Route> solutionRoutes = this.solution == null ? new LinkedList<>() : this.solution.getRoutes();
            Collections.shuffle(solutionRoutes, ThreadLocalRandom.current());
            // Setters already queued in the pool when the stop arrived would otherwise each
            // walk the whole tour running local search, so the walk checks the flag too.
            while (i < this.graph.getLength() && !this.graph.getData().isStopRequested()) {
                length++;
                AuxiliaryGraphNode endingNode = this.graph.getNode(++i);
                if (this.solution != null && this.solution.getTotalDistance() >= endingNode.getLabel()) {
                    this.nodeProcessingWith++;
                    this.graph.setNewSetters(endingNode);
                    continue;
                }
                while (size < length) {
                    int stop = this.tour[j++ % this.graph.getLength()];
                    if (this.solution == null || !this.solution.contains(stop)) {
                        cumulativeDemand += this.graph.getData().getDemand(stop);
                        if (size == sequence.length)
                            sequence = Arrays.copyOf(sequence, 2 * size);
                        sequence[size++] = stop;
                    }
                }
                int[] sequenceAsArray = Arrays.copyOf(sequence, size);
                // The same stop sequence gives a different cost from every depot, so one
                // candidate route is grown per candidate depot and the node keeps the best.
                // ponytail: the constructor calls setCost, an O(length) walk per depot. Close
                // the depot legs onto an incremental inner distance if the split gets too slow.
                // Only depots with room for these stops on top of what they already ship can
                // host the route, so the others are not even built: the Route constructor
                // walks the whole sequence to cost it.
                Map<Depot, Route> candidates = new HashMap<>(depots.length, 1f);
                for (Depot depot : depots) {
                    if (cumulativeDemand > this.leftOver(depot))
                        continue;
                    Route candidate = new Route(this.graph.getData(), this.solution, depot, sequenceAsArray.clone());
                    candidates.put(depot, candidate);
                    if (cumulativeDemand <= this.graph.getData().getCapacity()
                        && !endingNode.updateLabel(this.solution, candidate)) {
                        candidate.intraRoutesLocalSearch(this.graph.getData());
                        endingNode.updateLabel(this.solution, candidate);
                    }
                }
                for (Route oldRoute : solutionRoutes) {
                    final int combinedDemand = oldRoute.getSumDemand() + cumulativeDemand;
                    // Extending a route leaves its depot serving the new stops as well, so the
                    // depot has to have room for them on top of everything it already ships.
                    final boolean depotHasRoom = cumulativeDemand <= this.leftOver(oldRoute.getDepot());
                    if (combinedDemand <= this.graph.getData().getCapacity() && depotHasRoom) {
                        int[] combinedSequence1 = new int[oldRoute.getLength() + length];
                        System.arraycopy(oldRoute.getSequence(), 0, combinedSequence1, 0, oldRoute.getLength());
                        System.arraycopy(sequenceAsArray, 0, combinedSequence1, oldRoute.getLength(), length);
                        // The combined route takes the place of oldRoute, so it takes over its
                        // share of the depot opening cost rather than paying it a second time.
                        Route combinedRoute1 = new Route(this.graph.getData(), oldRoute.getDepot(),
                                                          combinedSequence1, oldRoute.paysDepotOpening());
                        if (!endingNode.updateLabel(this.solution, oldRoute, combinedRoute1)) {
                            combinedRoute1.intraRoutesLocalSearch(this.graph.getData());
                            endingNode.updateLabel(this.solution, oldRoute, combinedRoute1);
                        }
                        int[] combinedSequence2 = new int[oldRoute.getLength() + length];
                        System.arraycopy(sequenceAsArray, 0, combinedSequence2, 0, length);
                        System.arraycopy(oldRoute.getSequence(), 0, combinedSequence2, length, oldRoute.getLength());
                        Route combinedRoute2 = new Route(this.graph.getData(), oldRoute.getDepot(),
                                                          combinedSequence2, oldRoute.paysDepotOpening());
                        if (!endingNode.updateLabel(this.solution, oldRoute, combinedRoute2)) {
                            combinedRoute2.intraRoutesLocalSearch(this.graph.getData());
                            endingNode.updateLabel(this.solution, oldRoute, combinedRoute2);
                        }
                    }
                    // Unlike merging, a swap or a shift leaves the two routes on their own
                    // depots and only moves stops between them, so the segment is offered to
                    // every depot that could host it and not just to the one already serving
                    // oldRoute. The move itself checks the receiving depot has room.
                    if (combinedDemand <= 2 * this.graph.getData().getCapacity())
                        for (Route candidate : candidates.values()) {
                            LocalSearchMove lsm = oldRoute.getLSM(this.graph.getData(), candidate, this.solution);
                            if (lsm != null) {
                                lsm.perform(this.graph.getData());
                                endingNode.updateLabel(this.graph.getData(), this.solution, oldRoute, lsm.getFirstRoute(), lsm.getSecondRoute());
                                break;
                            }
                        }
                }
                if (cumulativeDemand > this.graph.getData().getCapacity()) {
                    this.nodeProcessingWith = this.graph.getLength();
                    this.graph.setNewSetters(endingNode);
                    break;
                }
                this.nodeProcessingWith++;
                this.graph.setNewSetters(endingNode);
            }

        } finally {
            this.graph.getPhaser().arriveAndDeregister();
            this.graph.getArcsSetters().remove(this);
        }
    }

    /**
     * @param depot a candidate depot
     * @return the demand that depot can still take, its whole capacity at the
     *         source where there is no partial solution yet
     */
    private int leftOver(Depot depot) {
        return this.solution == null ? depot.capacity() : this.solution.getLeftOver(depot);
    }

    // The giant tour is compared by reference in equals, so it is hashed by identity to match.
    @Override
    public int hashCode() {
        int hash = this.startingNode.nodeIndex;
        if (this.graph.getTours().length > 1)
            hash = 31 * hash + System.identityHashCode(this.tour);
        return this.solution != null ? 31 * hash + Double.hashCode(this.solution.getTotalDistance()) : hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ArcSetter other = (ArcSetter) obj;
        if (this.startingNode.nodeIndex != other.startingNode.nodeIndex)
            return false;
        if (this.graph.getTours().length > 1 && this.tour != other.tour)
            return false;
        return this.solution == null ? other.solution == null : this.solution.getTotalDistance() == other.solution.getTotalDistance() && this.solution.getRoutesCount() == other.solution.getRoutesCount();
    }
}
