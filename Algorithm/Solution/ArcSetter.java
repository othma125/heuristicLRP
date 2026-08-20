// Author: Othmane

package Algorithm.Solution;

import Algorithm.Data.Depot;
import Algorithm.Solution.LSM.LocalSearchMove;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.RecursiveAction;

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
    final AuxiliaryGraphNode StartingNode;
    final GiantTour GiantTour;
    final Solution Solution;
    volatile int NodeProcessingWith;

    /**
     * @param graph   the auxiliary graph this setter belongs to
     * @param node    the node this setter starts from
     * @param solution the partial solution reaching {@code node}, or {@code null} for the source
     * @param gt      the giant tour whose ordering guides route growth
     */
    ArcSetter(AuxiliaryGraph graph, AuxiliaryGraphNode node, Solution solution, GiantTour gt) {
        this.graph = graph;
        this.StartingNode = node;
        this.Solution = solution;
        this.GiantTour = gt;
        this.NodeProcessingWith = this.StartingNode.NodeIndex;
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
            int i = this.StartingNode.NodeIndex;
            int j = this.StartingNode.NodeIndex;
            int length = 0;
            int cumulative_demand = 0;
            final List<Integer> sequence_as_list = new LinkedList<>();
            final Depot[] depots = this.graph.getData().getDepots();
            // The solution's routes do not change while this setter walks the tour, so the list
            // is taken once. It is shuffled because the scan below stops at the first improving
            // merge, and a fixed order would always try the same routes first.
            final List<Route> solution_routes = this.Solution == null ? new LinkedList<>() : this.Solution.getRoutes();
            Collections.shuffle(solution_routes, ThreadLocalRandom.current());
            // Setters already queued in the pool when the stop arrived would otherwise each
            // walk the whole tour running local search, so the walk checks the flag too.
            while (i < this.graph.getLength() && !this.graph.getData().isStopRequested()) {
                length++;
                AuxiliaryGraphNode EndingNode = this.graph.getNode(++i);
                if (this.Solution != null && this.Solution.getTotalDistance() >= EndingNode.getLabel()) {
                    this.NodeProcessingWith++;
                    this.graph.setNewSetters(EndingNode);
                    continue;
                }
                while (sequence_as_list.size() < length) {
                    int stop = this.GiantTour.getStop(j++ % this.graph.getLength());
                    if (this.Solution == null || !this.Solution.contains(stop)) {
                        cumulative_demand += this.graph.getData().getDemand(stop);
                        sequence_as_list.add(stop);
                    }
                }
                int[] sequence_as_array = sequence_as_list.stream().mapToInt(Integer::intValue).toArray();
                // The same stop sequence gives a different cost from every depot, so one
                // candidate route is grown per candidate depot and the node keeps the best.
                // ponytail: the constructor calls setCost, an O(length) walk per depot. Close
                // the depot legs onto an incremental inner distance if the split gets too slow.
                // Only depots with room for these stops on top of what they already ship can
                // host the route, so the others are not even built: the Route constructor
                // walks the whole sequence to cost it.
                Map<Depot, Route> candidates = new HashMap<>(depots.length, 1f);
                for (Depot depot : depots) {
                    if (cumulative_demand > this.leftOver(depot))
                        continue;
                    Route candidate = new Route(this.graph.getData(), this.Solution, depot, sequence_as_array.clone());
                    candidates.put(depot, candidate);
                    if (cumulative_demand <= this.graph.getData().getCapacity()
                        && !EndingNode.UpdateLabel(this.Solution, candidate)) {
                        candidate.IntraRoutesLocalSearch(this.graph.getData());
                        EndingNode.UpdateLabel(this.Solution, candidate);
                    }
                }
                boolean c = true;
                for (Route old_route : solution_routes) {
                    // Merging extends an existing route, so the merged route keeps its depot.
                    // Present whenever depot_has_room below holds: same test as the one
                    // that filled the map.
                    Route new_route = candidates.get(old_route.getDepot());
                    final int combined_demand = old_route.getSumDemand() + cumulative_demand;
                    // Extending a route leaves its depot serving the new stops as well, so the
                    // depot has to have room for them on top of everything it already ships.
                    final boolean depot_has_room = cumulative_demand <= this.leftOver(old_route.getDepot());
                    if (combined_demand <= this.graph.getData().getCapacity() && depot_has_room) {
                        int[] combined_sequence1 = new int[old_route.getLength() + length];
                        for (int index = 0; index < combined_sequence1.length; index++) {
                            if (index < old_route.getLength())
                                combined_sequence1[index] = old_route.getStop(index);
                            else
                                combined_sequence1[index] = sequence_as_array[index - old_route.getLength()];
                        }
                        // The combined route takes the place of old_route, so it takes over its
                        // share of the depot opening cost rather than paying it a second time.
                        Route combined_route1 = new Route(this.graph.getData(), old_route.getDepot(),
                                                          combined_sequence1, old_route.paysDepotOpening());
                        if (!EndingNode.UpdateLabel(this.Solution, old_route, combined_route1)) {
                            combined_route1.IntraRoutesLocalSearch(this.graph.getData());
                            EndingNode.UpdateLabel(this.Solution, old_route, combined_route1);
                        }
                        int[] combined_sequence2 = new int[old_route.getLength() + length];
                        for (int index = 0; index < combined_sequence2.length; index++) {
                            if (index < sequence_as_array.length)
                                combined_sequence2[index] = sequence_as_array[index];
                            else
                                combined_sequence2[index] = old_route.getStop(index - sequence_as_array.length);
                        }
                        Route combined_route2 = new Route(this.graph.getData(), old_route.getDepot(),
                                                          combined_sequence2, old_route.paysDepotOpening());
                        if (!EndingNode.UpdateLabel(this.Solution, old_route, combined_route2)) {
                            combined_route2.IntraRoutesLocalSearch(this.graph.getData());
                            EndingNode.UpdateLabel(this.Solution, old_route, combined_route2);
                        }
                    }
                    if (combined_demand <= 2 * this.graph.getData().getCapacity() && depot_has_room) {
                        c = false;
                        LocalSearchMove lsm = old_route.getLSM(this.graph.getData(), new_route);
                        if (lsm != null) {
                            lsm.Perform(this.graph.getData());
                            EndingNode.UpdateLabel(this.graph.getData(), this.Solution, old_route, lsm.getFirstRoute(), lsm.getSecondRoute());
                        }
                    }
                }
                if (c && cumulative_demand > this.graph.getData().getCapacity()) {
                    this.NodeProcessingWith = this.graph.getLength();
                    this.graph.setNewSetters(EndingNode);
                    break;
                }
                this.NodeProcessingWith++;
                this.graph.setNewSetters(EndingNode);
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
        return this.Solution == null ? depot.capacity() : this.Solution.getLeftOver(depot);
    }

    @Override
    public int hashCode() {
        int hash = this.StartingNode.NodeIndex;
        if (this.graph.getGiantTours().length > 1)
            hash = 31 * hash + this.GiantTour.getStop(this.StartingNode.NodeIndex);
        return this.Solution != null ? 31 * hash + Double.hashCode(this.Solution.getTotalDistance()) : hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ArcSetter other = (ArcSetter) obj;
        if (this.StartingNode.NodeIndex != other.StartingNode.NodeIndex)
            return false;
        if (this.graph.getGiantTours().length > 1 && this.GiantTour.getStop(this.StartingNode.NodeIndex) != other.GiantTour.getStop(other.StartingNode.NodeIndex))
            return false;
        return this.Solution == null ? other.Solution == null : this.Solution.getTotalDistance() == other.Solution.getTotalDistance() && this.Solution.getRoutesCount() == other.Solution.getRoutesCount();
    }
}
